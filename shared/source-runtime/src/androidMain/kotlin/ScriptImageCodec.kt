package app.koyura.source.runtime

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

internal actual fun decodeScriptImage(bytes: ByteArray): PixelImage {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val width = bounds.outWidth
    val height = bounds.outHeight
    val pixels = IntArray(imagePixelCount(width, height))
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Cannot decode image")
    try {
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return PixelImage(width, height, pixels)
    } finally {
        bitmap.recycle()
    }
}

internal actual fun encodeScriptImage(image: PixelImage): ByteArray {
    val bitmap = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
    try {
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
    } finally {
        bitmap.recycle()
    }
}
