package app.koyura.source.runtime

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

internal actual fun decodeScriptImage(bytes: ByteArray): PixelImage =
    MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
        val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: error("Cannot decode image")
        try {
            reader.input = input
            val width = reader.getWidth(0)
            val height = reader.getHeight(0)
            val pixels = IntArray(imagePixelCount(width, height))
            reader.read(0).getRGB(0, 0, width, height, pixels, 0, width)
            PixelImage(width, height, pixels)
        } finally {
            reader.dispose()
        }
    }

internal actual fun encodeScriptImage(image: PixelImage): ByteArray {
    val bitmap = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
    bitmap.setRGB(0, 0, image.width, image.height, image.pixels, 0, image.width)
    return ByteArrayOutputStream().use { output ->
        check(ImageIO.write(bitmap, "png", output))
        output.toByteArray()
    }
}
