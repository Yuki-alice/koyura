package app.koyura.app.reader.viewer

import app.koyura.domain.reader.ChapterContent
import app.koyura.features.reader.comic.FakeReaderService
import app.koyura.features.reader.comic.ReaderSession

/**
 * Fixed two-chapter fixture for B-viewer acceptance.
 *
 * Chapter 1: three portrait pages (cross-chapter navigation).
 * Chapter 2: seven numbered pages —
 * - q0 portrait cover,
 * - q1 landscape spread (wide, stays solo when doubled),
 * - q2 portrait,
 * - q3 long strip (tall),
 * - q4 portrait,
 * - q5 portrait that fails on first load, then retries clean,
 * - q6 portrait odd tail.
 */
object ViewerFixture {
    const val SOURCE = "viewer_source"
    const val WORK = "viewer_work"
    const val CHAPTER_1 = "ep_1"
    const val CHAPTER_2 = "ep_2"

    val chapterIds = listOf(CHAPTER_1, CHAPTER_2)

    fun service(): FakeReaderService {
        val bytes = mutableMapOf<String, ByteArray>()
        // Chapter 1: plain portraits.
        bytes["p0"] = numberedPng(400, 600, 0xB03A2E, "1-1")
        bytes["p1"] = numberedPng(400, 600, 0x1E8449, "1-2")
        bytes["p2"] = numberedPng(400, 600, 0x2471A3, "1-3")
        // Chapter 2: mixed aspects.
        bytes["q0"] = numberedPng(400, 600, 0x7D3C98, "2-1")
        bytes["q1"] = numberedPng(800, 400, 0xCA6F1E, "2-2 WIDE")
        bytes["q2"] = numberedPng(400, 600, 0x2E86C1, "2-3")
        bytes["q3"] = numberedPng(400, 1200, 0x117A65, "2-4 LONG")
        bytes["q4"] = numberedPng(400, 600, 0xB7950B, "2-5")
        bytes["q5"] = numberedPng(400, 600, 0x943126, "2-6 RETRY")
        bytes["q6"] = numberedPng(400, 600, 0x515A5A, "2-7 TAIL")
        val service = FakeReaderService(
            chapters = mutableMapOf(
                Triple(SOURCE, WORK, CHAPTER_1) to ChapterContent(
                    chapterId = CHAPTER_1,
                    title = "Chapter 1",
                    pageKeys = listOf("p0", "p1", "p2"),
                    startPageIndex = 0,
                ),
                Triple(SOURCE, WORK, CHAPTER_2) to ChapterContent(
                    chapterId = CHAPTER_2,
                    title = "Chapter 2",
                    pageKeys = listOf("q0", "q1", "q2", "q3", "q4", "q5", "q6"),
                    startPageIndex = 0,
                ),
            ),
            pageBytes = bytes,
        )
        // q5 fails once, then succeeds on retry.
        service.pageFailures["q5"] = 1
        return service
    }

    fun session(service: FakeReaderService): ReaderSession = ReaderSession(service)

    const val CROP_CHAPTER = "crop_ch"
    val cropChapterIds = listOf(CROP_CHAPTER)

    /**
     * Crop fixture: one bordered page (40px white frame with corner marks inside
     * the content) plus one borderless page whose corners hold content. Both keep
     * their page keys and positions with crop on or off.
     */
    fun cropService(): FakeReaderService = FakeReaderService(
        chapters = mutableMapOf(
            Triple(SOURCE, WORK, CROP_CHAPTER) to ChapterContent(
                chapterId = CROP_CHAPTER,
                title = "Crop",
                pageKeys = listOf("cb0", "cb1"),
                startPageIndex = 0,
            ),
        ),
        pageBytes = mutableMapOf(
            "cb0" to framedPng(400, 520, 40, 0xFFFFFF, 0x117A65, "C-1"),
            "cb1" to cornerMarkedPng(400, 600, 0x2E86C1, "C-2"),
        ),
    )
}

/** Numbered PNG for viewer tests (decoded by Skia on JVM). */
fun numberedPng(width: Int, height: Int, rgb: Int, label: String): ByteArray {
    val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.color = java.awt.Color(rgb)
    graphics.fillRect(0, 0, width, height)
    graphics.color = java.awt.Color.WHITE
    // Fit the label inside the bitmap: long-strip pages are narrow, so size the font
    // by the full label length, not the height.
    val fontSize = (width / (label.length * 0.7)).toInt().coerceIn(18, height / 8)
    graphics.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, fontSize)
    val metrics = graphics.fontMetrics
    val x = (width - metrics.stringWidth(label)) / 2
    val y = height / 2
    graphics.drawString(label, x.coerceAtLeast(8), y)
    // Border so page edges are visible against the canvas.
    graphics.color = java.awt.Color.BLACK
    graphics.drawRect(0, 0, width - 1, height - 1)
    graphics.dispose()
    val out = java.io.ByteArrayOutputStream()
    javax.imageio.ImageIO.write(image, "png", out)
    return out.toByteArray()
}

/**
 * Bordered page for crop tests: a solid [border]-px frame around a filled
 * content rect with a centered label and contrasting corner squares just
 * inside the content (they must survive border removal).
 */
fun framedPng(
    width: Int,
    height: Int,
    border: Int,
    borderRgb: Int,
    fillRgb: Int,
    label: String,
): ByteArray {
    val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.color = java.awt.Color(borderRgb)
    graphics.fillRect(0, 0, width, height)
    graphics.color = java.awt.Color(fillRgb)
    graphics.fillRect(border, border, width - 2 * border, height - 2 * border)
    // Corner squares inside the content rect (must survive cropping).
    graphics.color = java.awt.Color(0xFFFF0000.toInt())
    val m = 14
    graphics.fillRect(border, border, m, m)
    graphics.fillRect(width - border - m, border, m, m)
    graphics.fillRect(border, height - border - m, m, m)
    graphics.fillRect(width - border - m, height - border - m, m, m)
    graphics.color = java.awt.Color.WHITE
    val fontSize = ((width - 2 * border) / (label.length * 0.7)).toInt().coerceIn(18, (height - 2 * border) / 8)
    graphics.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, fontSize)
    val metrics = graphics.fontMetrics
    val x = border + (width - 2 * border - metrics.stringWidth(label)) / 2
    graphics.drawString(label, x.coerceAtLeast(border + 4), height / 2)
    graphics.dispose()
    val out = java.io.ByteArrayOutputStream()
    javax.imageio.ImageIO.write(image, "png", out)
    return out.toByteArray()
}

/**
 * Borderless page with contrasting corner squares at the bitmap corners and
 * a centered label. Crop must leave every pixel untouched.
 */
fun cornerMarkedPng(width: Int, height: Int, rgb: Int, label: String): ByteArray {
    val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.color = java.awt.Color(rgb)
    graphics.fillRect(0, 0, width, height)
    graphics.color = java.awt.Color(0xFFFFFF00.toInt())
    val m = 14
    graphics.fillRect(0, 0, m, m)
    graphics.fillRect(width - m, 0, m, m)
    graphics.fillRect(0, height - m, m, m)
    graphics.fillRect(width - m, height - m, m, m)
    graphics.color = java.awt.Color.WHITE
    val fontSize = (width / (label.length * 0.7)).toInt().coerceIn(18, height / 8)
    graphics.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, fontSize)
    val metrics = graphics.fontMetrics
    val x = (width - metrics.stringWidth(label)) / 2
    graphics.drawString(label, x.coerceAtLeast(8), height / 2)
    graphics.dispose()
    val out = java.io.ByteArrayOutputStream()
    javax.imageio.ImageIO.write(image, "png", out)
    return out.toByteArray()
}
