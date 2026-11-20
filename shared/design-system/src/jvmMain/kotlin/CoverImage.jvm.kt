package app.koyura.designsystem

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import coil3.Image
import coil3.asImage

internal actual fun ImageBitmap.toCoverImage(): Image = asSkiaBitmap().asImage()
