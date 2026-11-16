package app.koyura.features.reader.comic

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun ByteArray.decodeToImageBitmap(): ImageBitmap {
    val bitmap = BitmapFactory.decodeByteArray(this, 0, size)
        ?: throw IllegalArgumentException("Unsupported image bytes")
    return bitmap.asImageBitmap()
}
