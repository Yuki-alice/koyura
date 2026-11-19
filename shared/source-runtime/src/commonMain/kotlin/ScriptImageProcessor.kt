package app.koyura.source.runtime

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

private val imageWorkers = Semaphore(2)
internal const val MAX_IMAGE_PIXELS = 24_000_000
internal data class PixelImage(val width: Int, val height: Int, val pixels: IntArray)

internal fun imagePixelCount(width: Int, height: Int): Int {
    val count = width.toLong() * height
    require(width > 0 && height > 0 && count <= MAX_IMAGE_PIXELS) { "Invalid image dimensions" }
    return count.toInt()
}

internal expect fun decodeScriptImage(bytes: ByteArray): PixelImage
internal expect fun encodeScriptImage(image: PixelImage): ByteArray

/** A decoded image transform runs with only the image bridge in a fresh QuickJS engine. */
suspend fun modifyImageWithScript(bytes: ByteArray, script: String): ByteArray =
    withContext(Dispatchers.Default) {
        imageWorkers.withPermit {
            val operations = ImageOperations(decodeScriptImage(bytes))
            val engine = QuickJs.create(Dispatchers.Default)
            try {
                engine.memoryLimit = 16L * 1024 * 1024
                engine.evaluationTimeoutMillis = 5_000
                engine.function<String, Int>("__imageOperation", operations::dispatch)
                val id = engine.evaluate<Int>(
                    """
                    $imageScriptBootstrap
                    $script
                    ;(() => {
                        const result = modifyImage(new Image(0));
                        if (!(result instanceof Image)) throw new TypeError("modifyImage must return an Image synchronously");
                        return result.key;
                    })();
                    """.trimIndent(),
                    filename = "image:modifyImage",
                )
                encodeScriptImage(operations.image(id))
            } finally {
                engine.close()
            }
        }
    }

private class ImageOperations(input: PixelImage) {
    private val images = mutableListOf(input)
    private var remainingPixels = MAX_IMAGE_PIXELS * 2 - input.pixels.size

    fun image(id: Int) = images[id]

    fun dispatch(payload: String): Int {
        val args = Json.parseToJsonElement(payload).jsonArray
        fun int(index: Int) = args[index].jsonPrimitive.int
        fun canvas() = image(int(1))
        return when (args[0].jsonPrimitive.content) {
            "width" -> canvas().width
            "height" -> canvas().height
            "empty" -> allocate(int(1), int(2))
            "copy" -> {
                val target = allocate(int(4), int(5))
                copy(image(target), 0, 0, canvas(), int(2), int(3), int(4), int(5))
                target
            }
            "rotate" -> {
                val source = canvas()
                val target = allocate(source.height, source.width)
                val rotated = image(target)
                for (y in 0 until source.height) {
                    for (x in 0 until source.width) {
                        rotated.pixels[x * source.height + source.height - y - 1] = source.pixels[y * source.width + x]
                    }
                }
                target
            }
            "fill" -> {
                val source = image(int(4))
                copy(canvas(), int(2), int(3), source, 0, 0, source.width, source.height)
                0
            }
            "fillRange" -> {
                copy(canvas(), int(2), int(3), image(int(4)), int(5), int(6), int(7), int(8))
                0
            }
            else -> error("Unknown image operation")
        }
    }

    private fun allocate(width: Int, height: Int): Int {
        val pixels = imagePixelCount(width, height)
        require(pixels <= remainingPixels && images.size < 1024) { "Image transform exceeded its allocation limit" }
        remainingPixels -= pixels
        images.add(PixelImage(width, height, IntArray(pixels)))
        return images.lastIndex
    }

    private fun copy(
        target: PixelImage,
        x: Int,
        y: Int,
        source: PixelImage,
        sourceX: Int,
        sourceY: Int,
        width: Int,
        height: Int,
    ) {
        require(width >= 0 && height >= 0)
        require(x >= 0 && y >= 0 && x <= target.width - width && y <= target.height - height)
        require(sourceX >= 0 && sourceY >= 0 && sourceX <= source.width - width && sourceY <= source.height - height)
        for (row in 0 until height) {
            val from = (sourceY + row) * source.width + sourceX
            val to = (y + row) * target.width + x
            if (source === target) {
                for (column in 0 until width) target.pixels[to + column] = source.pixels[from + column]
            } else {
                source.pixels.copyInto(target.pixels, to, from, from + width)
            }
        }
    }
}

private val imageScriptBootstrap = """
    const Image = (() => {
        const operation = __imageOperation;
        function call(...args) { return operation(JSON.stringify(args)); }
        return class Image {
            constructor(key) { this.key = key; }
            get width() { return call("width", this.key); }
            get height() { return call("height", this.key); }
            static empty(width, height) { return new Image(call("empty", width, height)); }
            copyRange(x, y, width, height) { return new Image(call("copy", this.key, x, y, width, height)); }
            copyAndRotate90() { return new Image(call("rotate", this.key)); }
            fillImageAt(x, y, image) { call("fill", this.key, x, y, image.key); }
            fillImageRangeAt(x, y, image, sx, sy, width, height) {
                call("fillRange", this.key, x, y, image.key, sx, sy, width, height);
            }
        };
    })();
""".trimIndent()
