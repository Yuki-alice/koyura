package app.koyura.features.reader.comic

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decodes encoded image bytes (PNG, JPEG, WebP, GIF first frame) into an [ImageBitmap].
 *
 * Throws when the bytes do not describe a supported image.
 */
expect fun ByteArray.decodeToImageBitmap(): ImageBitmap
