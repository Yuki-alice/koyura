package app.koyura.source.runtime

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

@OptIn(ExperimentalForeignApi::class)
internal actual fun decodeScriptImage(bytes: ByteArray): PixelImage {
    require(bytes.isNotEmpty())
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    val image = checkNotNull(UIImage(data = data).CGImage) { "Cannot decode image" }
    val width = CGImageGetWidth(image).toInt()
    val height = CGImageGetHeight(image).toInt()
    val pixels = IntArray(imagePixelCount(width, height))
    withBitmapContext(width, height, pixels) { context ->
        CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), image)
    }
    return PixelImage(width, height, pixels)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun encodeScriptImage(image: PixelImage): ByteArray =
    withBitmapContext(image.width, image.height, image.pixels) { context ->
        val native = checkNotNull(CGBitmapContextCreateImage(context))
        try {
            val png = checkNotNull(UIImagePNGRepresentation(UIImage.imageWithCGImage(native)))
            png.bytes!!.readBytes(png.length.toInt())
        } finally {
            CGImageRelease(native)
        }
    }

@OptIn(ExperimentalForeignApi::class)
private fun <T> withBitmapContext(
    width: Int,
    height: Int,
    pixels: IntArray,
    use: (platform.CoreGraphics.CGContextRef) -> T,
): T = pixels.usePinned { pinned ->
    val colorSpace = checkNotNull(CGColorSpaceCreateDeviceRGB())
    try {
        val context = checkNotNull(CGBitmapContextCreate(
            pinned.addressOf(0), width.toULong(), height.toULong(), 8uL, width.toULong() * 4uL,
            colorSpace, kCGBitmapByteOrder32Little or CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst.value,
        ))
        try {
            use(context)
        } finally {
            CGContextRelease(context)
        }
    } finally {
        CGColorSpaceRelease(colorSpace)
    }
}
