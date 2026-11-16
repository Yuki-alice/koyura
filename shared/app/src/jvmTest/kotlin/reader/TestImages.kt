package app.koyura.app.reader

/** Builds a small valid PNG for image tests (decoded by Skia in the reader UI). */
internal fun pngBytes(rgb: Int): ByteArray {
    val image = java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.color = java.awt.Color(rgb)
    graphics.fillRect(0, 0, 4, 4)
    graphics.dispose()
    val out = java.io.ByteArrayOutputStream()
    javax.imageio.ImageIO.write(image, "png", out)
    return out.toByteArray()
}
