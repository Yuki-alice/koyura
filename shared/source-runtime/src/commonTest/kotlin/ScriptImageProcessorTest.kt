package app.koyura.source.runtime

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ScriptImageProcessorTest {
    @Test
    fun cropsRotatesAndReassemblesPixelsInAnIndependentEngine() = runTest {
        val red = 0xffff0000.toInt()
        val green = 0xff00ff00.toInt()
        val blue = 0xff0000ff.toInt()
        val white = 0xffffffff.toInt()
        val original = encodeScriptImage(PixelImage(2, 2, intArrayOf(red, green, blue, white)))
        val transformed = modifyImageWithScript(original, """
            function modifyImage(image) {
                if (typeof ComicSource !== "undefined" || typeof Network !== "undefined") throw "Main engine leaked";
                const result = Image.empty(image.height, image.width);
                const top = image.copyRange(0, 0, 2, 1).copyAndRotate90();
                result.fillImageAt(1, 0, top);
                const bottom = image.copyAndRotate90();
                result.fillImageRangeAt(0, 0, bottom, 0, 0, 1, 2);
                return result;
            }
        """.trimIndent())
        val image = decodeScriptImage(transformed)
        assertEquals(2, image.width)
        assertEquals(2, image.height)
        assertContentEquals(intArrayOf(blue, red, white, green), image.pixels)
    }
}
