package app.koyura.app.reader.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import app.koyura.designsystem.ReaderTheme
import app.koyura.features.reader.comic.ReaderContentState
import app.koyura.features.reader.comic.ReaderSession
import app.koyura.features.reader.comic.viewer.ComicPagerLayout
import app.koyura.features.reader.comic.viewer.ComicReadingMode
import app.koyura.features.reader.comic.viewer.ComicScaleFit
import app.koyura.features.reader.comic.viewer.ComicViewer
import app.koyura.features.reader.comic.viewer.ComicViewerJump
import app.koyura.features.reader.comic.viewer.ComicViewerOptions
import java.awt.AlphaComposite
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat

/**
 * Same-data, same-canvas ORIGINAL-side panels plus Koyura-vs-original sheets.
 *
 * The reference Komikku app is an Android View app that cannot run in this JVM
 * host (its repo ships no reader screenshots either), so the original side is a
 * schematic reconstruction drawn with the same fixture bytes on the same canvas
 * sizes, with every layout decision tied to the original source cited below.
 * Sheets are labelled KOYURA (real capture) vs ORIGINAL (code-derived schematic);
 * nothing here claims to be a device capture of the original app.
 *
 * Cited original behaviour (reference/komikku @383ffaa816,
 * app/src/main/java/eu/kanade/):
 * - Pager tap mapping: PagerViewer tapListener maps LEFT→moveLeft,
 *   RIGHT→moveRight, MENU→toggleMenu; regions come from
 *   navigation/RightAndLeftNavigation (pager) and navigation/LNavigation
 *   (vertical pager, webtoon).
 * - RTL: R2LPagerViewer reverses the adapter (setJoinedItems reverses when the
 *   viewer is R2L) and swaps moveToNext/moveToPrevious to moveLeft/moveRight.
 * - Double pages: PagerViewerAdapter.setJoinedItems pairs adjacent pages;
 *   PagerPageHolder.mergePages lays them out with
 *   `isLTR = (viewer !is R2LPagerViewer) xor invertDoublePages`; wide pages are
 *   isolated solo via splitDoublePages/fullPage.
 * - AUTOMATIC: ReaderActivity.setDoublePageMode doubles only in landscape.
 * - WEBTOON vs CONTINUOUS_VERTICAL: WebtoonPageHolder.refreshLayoutParams adds
 *   `bottomMargin = 15.dpToPx` only when `!viewer.isContinuous`; ReadingMode maps
 *   WEBTOON to WebtoonViewer(isContinuous=true) and CONTINUOUS_VERTICAL to
 *   WebtoonViewer(isContinuous=false).
 * - Failure: PagerPageHolder.showErrorLayout binds the decode/load error plus
 *   actionRetry → pageLoader.retryPage (web-open button only for http urls,
 *   omitted here since the fixture is local).
 * - Fits map to the original ImageScaleType list (fit screen / fit width /
 *   fit height / original size).
 */
class OriginalReferenceShotsTest {
    private val outputDir = File(System.getProperty("reader.screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    private val bytes: Map<String, ByteArray> by lazy {
        ViewerFixture.service().let { service ->
            // Same bytes the Koyura host decodes: the service is rebuilt per test,
            // so snapshot the map once here for the schematics.
            service.pageBytes.toMap()
        }
    }

    private fun img(key: String): BufferedImage =
        ImageIO.read(ByteArrayInputStream(bytes.getValue(key)))

    // ------------------------------------------------------------------
    // Koyura side (real capture, same host as the screenshot test)
    // ------------------------------------------------------------------

    /**
     * Renders the viewer after driving a sequence of jumps to rest.
     *
     * Multi-step jumps matter for double-wide spreads: landing on the wide page
     * first lets it decode, which splits the spread and re-anchors the viewer, so
     * the following jump shows the post-split pairing (the steady state the
     * original schematic depicts). Each step is pumped until the reported page
     * stops changing.
     */
    private fun koyuraBytesSequenced(
        mode: ComicReadingMode,
        layout: ComicPagerLayout = ComicPagerLayout.SINGLE_PAGE,
        invert: Boolean = false,
        fit: ComicScaleFit = ComicScaleFit.FIT,
        jumps: List<Int> = emptyList(),
        widthDp: Int = 420,
        heightDp: Int = 780,
    ): ByteArray {
        val service = ViewerFixture.service()
        val session = ViewerFixture.session(service)
        runBlocking {
            session.open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds)
        }
        val content = (session.content as? ReaderContentState.Ready)?.content
            ?: error("chapter not opened")
        val options = ComicViewerOptions(
            mode = mode,
            pagerLayout = layout,
            firstPageSingle = true,
            invertDoublePages = invert,
            scaleFit = fit,
        )
        val holder = JumpHolder()
        val shot = arrayOf<ByteArray?>(null)
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(width = widthDp, height = heightDp, density = Density(1f)) {
                ReaderTheme(darkTheme = false) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        ComicViewer(
                            session = session,
                            content = content,
                            options = options,
                            jump = holder.jump,
                            onToggleMenu = {},
                            onChapterBoundary = {},
                        )
                    }
                }
            }
            try {
                for ((step, page) in jumps.withIndex()) {
                    holder.jump = ComicViewerJump(
                        requestId = 9300L + step,
                        contentSeq = session.contentSeq,
                        pageIndex = page,
                    )
                    settleJump(scene, session)
                }
                val image = scene.render(System.nanoTime())
                shot[0] = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            } finally {
                scene.close()
            }
        }
        return shot[0] ?: error("no render for $mode")
    }

    private class JumpHolder {
        var jump by androidx.compose.runtime.mutableStateOf<ComicViewerJump?>(null)
    }

    /** Renders frames until the reported page stops changing (jump fully settled). */
    private fun settleJump(scene: ImageComposeScene, session: ReaderSession) {
        var last = -1
        var stable = 0
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            scene.render(System.nanoTime())
            val current = session.currentPage
            if (current == last) {
                stable++
                if (stable >= 4) return
            } else {
                last = current
                stable = 0
            }
        }
    }

    // ------------------------------------------------------------------
    // Original side (java.awt schematics, same bytes, same canvas)
    // ------------------------------------------------------------------

    private fun canvas(w: Int, h: Int): BufferedImage {
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = java.awt.Color.BLACK
        g.fillRect(0, 0, w, h)
        g.dispose()
        return image
    }

    private fun drawFit(g: java.awt.Graphics2D, src: BufferedImage, x: Int, y: Int, w: Int, h: Int) {
        val s = minOf(w / src.width.toFloat(), h / src.height.toFloat())
        val dw = (src.width * s).toInt()
        val dh = (src.height * s).toInt()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, null)
    }

    private fun drawFillWidthTop(g: java.awt.Graphics2D, src: BufferedImage, x: Int, y: Int, w: Int) {
        val dh = (src.height * (w / src.width.toFloat())).toInt()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, x, y, w, dh, null)
    }

    private fun drawFillWidthCenterCrop(g: java.awt.Graphics2D, src: BufferedImage, x: Int, y: Int, w: Int, h: Int) {
        val dh = (src.height * (w / src.width.toFloat())).toInt()
        val clip = g.clip
        g.clipRect(x, y, w, h)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, x, y + (h - dh) / 2, w, dh, null)
        g.clip = clip
    }

    private fun caption(g: java.awt.Graphics2D, w: Int, text: String) {
        g.color = java.awt.Color(24, 24, 24)
        g.fillRect(0, 0, w, 44)
        g.color = java.awt.Color.WHITE
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 20)
        g.drawString(text, 12, 29)
    }

    /** Translucent tap-zone band with the original NavigationRegion colors. */
    private fun zone(
        g: java.awt.Graphics2D,
        x: Int, y: Int, w: Int, h: Int,
        rgb: Int, label: String,
    ) {
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f)
        g.color = java.awt.Color(rgb)
        g.fillRect(x, y, w, h)
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f)
        g.color = java.awt.Color.WHITE
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 22)
        val fm = g.fontMetrics
        g.drawString(label, x + (w - fm.stringWidth(label)) / 2, y + h / 2 + 8)
    }

    // Original NavigationRegion colors (ViewerNavigation.kt):
    // MENU 0xCC95818D, PREV 0xCCFF7733, NEXT 0xCC84E296,
    // LEFT 0xCC7D1128, RIGHT 0xCCA6CFD5 (alpha applied separately above).
    private object ZoneColor {
        const val MENU = 0x95818D
        const val PREV = 0xFF7733
        const val NEXT = 0x84E296
        const val LEFT = 0x7D1128
        const val RIGHT = 0xA6CFD5
    }

    private fun pagerZonesRtl(g: java.awt.Graphics2D, w: Int, h: Int, rtl: Boolean) {
        val third = w / 3
        // RightAndLeftNavigation: left=LEFT, right=RIGHT; R2L maps LEFT->moveLeft
        // (= next page) and RIGHT->moveRight (= previous page).
        zone(g, 0, 0, third, h, ZoneColor.LEFT, if (rtl) "NEXT" else "PREV")
        zone(g, third, 0, w - 2 * third, h, ZoneColor.MENU, "MENU")
        zone(g, 2 * third, 0, w - 2 * third, h, ZoneColor.RIGHT, if (rtl) "PREV" else "NEXT")
    }

    private fun pagerZonesVertical(g: java.awt.Graphics2D, w: Int, h: Int) {
        // LNavigation: top strip PREV, bottom strip NEXT, middle row PREV/MENU/NEXT.
        val band = h / 4
        zone(g, 0, 0, w, band, ZoneColor.PREV, "PREV")
        zone(g, 0, h - band, w, band, ZoneColor.NEXT, "NEXT")
        val midY = band
        val midH = h - 2 * band
        val third = w / 3
        zone(g, 0, midY, third, midH, ZoneColor.PREV, "PREV")
        zone(g, third, midY, w - 2 * third, midH, ZoneColor.MENU, "MENU")
        zone(g, 2 * third, midY, w - 2 * third, midH, ZoneColor.NEXT, "NEXT")
    }

    private fun errorLayout(g: java.awt.Graphics2D, w: Int, h: Int) {
        g.color = java.awt.Color(0xCF6679)
        g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 22)
        val msg = "Couldn't load page"
        g.drawString(msg, (w - g.fontMetrics.stringWidth(msg)) / 2, h / 2 - 20)
        val bw = 200
        val bh = 64
        g.color = java.awt.Color(0x6750A4)
        g.fillRoundRect((w - bw) / 2, h / 2 + 8, bw, bh, 32, 32)
        g.color = java.awt.Color.WHITE
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 24)
        val label = "Retry"
        g.drawString(label, (w - g.fontMetrics.stringWidth(label)) / 2, h / 2 + 8 + 42)
    }

    private fun origNarrow(draw: (java.awt.Graphics2D, Int, Int) -> Unit): BufferedImage {
        val image = canvas(420, 780)
        val g = image.createGraphics()
        draw(g, 420, 780)
        g.dispose()
        return image
    }

    private fun origWide(draw: (java.awt.Graphics2D, Int, Int) -> Unit): BufferedImage {
        val image = canvas(1280, 800)
        val g = image.createGraphics()
        draw(g, 1280, 800)
        g.dispose()
        return image
    }

    // ------------------------------------------------------------------
    // Sheets
    // ------------------------------------------------------------------

    private fun sheet(koyuraPng: ByteArray, orig: BufferedImage, left: String, right: String): BufferedImage {
        val koyura = ImageIO.read(ByteArrayInputStream(koyuraPng))
        val w = koyura.width
        val h = koyura.height
        require(orig.width == w && orig.height == h) { "canvas mismatch ${orig.width}x${orig.height} vs ${w}x$h" }
        val sheet = BufferedImage(2 * w, h + 56, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        g.color = java.awt.Color.BLACK
        g.fillRect(0, 0, 2 * w, h + 56)
        g.drawImage(koyura, 0, 56, null)
        g.drawImage(orig, w, 56, null)
        g.color = java.awt.Color.WHITE
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 24)
        g.drawString(left, 12, 36)
        val rw = g.fontMetrics.stringWidth(right)
        g.drawString(right, 2 * w - rw - 12, 36)
        g.dispose()
        return sheet
    }

    private fun write(name: String, image: BufferedImage) {
        ImageIO.write(image, "png", File(outputDir, "$name.png"))
    }

    private data class Shot(
        val name: String,
        val mode: ComicReadingMode,
        val layout: ComicPagerLayout = ComicPagerLayout.SINGLE_PAGE,
        val invert: Boolean = false,
        val fit: ComicScaleFit = ComicScaleFit.FIT,
        val jumpPage: Int? = null,
        /** Ordered jump pages; drives the Koyura side to steady state (e.g. post-split). */
        val jumpSteps: List<Int>? = null,
        val width: Int = 420,
        val height: Int = 780,
        val orig: (java.awt.Graphics2D, Int, Int) -> Unit,
        val koyuraLabel: String,
        val origLabel: String,
    ) {
        val jumps: List<Int> get() = jumpSteps ?: jumpPage?.let(::listOf) ?: emptyList()
    }

    @Test
    fun generateComparisons() {
        val shots = listOf(
            Shot("ltr-narrow", ComicReadingMode.LEFT_TO_RIGHT, orig = { g, w, h ->
                caption(g, w, "ORIG LTR q0 · single Fit")
                drawFit(g, img("q0"), 0, 44, w, h - 44)
                pagerZonesRtl(g, w, h, rtl = false)
            }, koyuraLabel = "KOYURA LTR", origLabel = "ORIGINAL LTR (code-derived)"),
            Shot("rtl-narrow", ComicReadingMode.RIGHT_TO_LEFT, orig = { g, w, h ->
                caption(g, w, "ORIG RTL q0 · reversed")
                drawFit(g, img("q0"), 0, 44, w, h - 44)
                pagerZonesRtl(g, w, h, rtl = true)
            }, koyuraLabel = "KOYURA RTL", origLabel = "ORIGINAL RTL (code-derived)"),
            Shot("vertical-narrow", ComicReadingMode.VERTICAL, orig = { g, w, h ->
                caption(g, w, "ORIG VERTICAL q0 · LNav")
                drawFit(g, img("q0"), 0, 44, w, h - 44)
                pagerZonesVertical(g, w, h)
            }, koyuraLabel = "KOYURA VERTICAL", origLabel = "ORIGINAL VERTICAL (code-derived)"),
            Shot("webtoon-narrow", ComicReadingMode.WEBTOON, orig = { g, w, h ->
                caption(g, w, "ORIG WEBTOON · no gaps")
                var y = 44
                for (key in listOf("q0", "q1", "q2")) {
                    val src = img(key)
                    val dh = (src.height * (w / src.width.toFloat())).toInt()
                    drawFillWidthTop(g, src, 0, y, w)
                    y += dh
                    if (y > h) break
                }
            }, koyuraLabel = "KOYURA WEBTOON", origLabel = "ORIGINAL WEBTOON (code-derived)"),
            Shot("continuous-narrow", ComicReadingMode.CONTINUOUS_VERTICAL, orig = { g, w, h ->
                caption(g, w, "ORIG CONT · 15dp gaps")
                var y = 44
                for (key in listOf("q0", "q1", "q2")) {
                    val src = img(key)
                    val dh = (src.height * (w / src.width.toFloat())).toInt()
                    drawFillWidthTop(g, src, 0, y, w)
                    y += dh + 15
                    if (y > h) break
                }
            }, koyuraLabel = "KOYURA CONTINUOUS", origLabel = "ORIGINAL CONTINUOUS (code-derived)"),
            Shot(
                "double-wide", ComicReadingMode.LEFT_TO_RIGHT, ComicPagerLayout.DOUBLE_PAGES,
                jumpSteps = listOf(1, 3), width = 1280, height = 800,
                orig = { g, w, h ->
                    caption(g, w, "ORIG DOUBLE [q2,q3] LTR")
                    val half = w / 2
                    drawFit(g, img("q2"), 0, 44, half, h - 44)
                    drawFit(g, img("q3"), half, 44, w - half, h - 44)
                }, koyuraLabel = "KOYURA DOUBLE", origLabel = "ORIGINAL DOUBLE (code-derived)",
            ),
            Shot(
                "double-wide-inverted", ComicReadingMode.RIGHT_TO_LEFT, ComicPagerLayout.DOUBLE_PAGES,
                invert = true, jumpSteps = listOf(1, 3), width = 1280, height = 800,
                orig = { g, w, h ->
                    caption(g, w, "ORIG DOUBLE [q3,q2] RTL")
                    val half = w / 2
                    drawFit(g, img("q3"), 0, 44, half, h - 44)
                    drawFit(g, img("q2"), half, 44, w - half, h - 44)
                }, koyuraLabel = "KOYURA DOUBLE INV", origLabel = "ORIGINAL DOUBLE INV (code-derived)",
            ),
            Shot(
                "double-wide-spread", ComicReadingMode.LEFT_TO_RIGHT, ComicPagerLayout.DOUBLE_PAGES,
                jumpPage = 1, width = 1280, height = 800,
                orig = { g, w, h ->
                    caption(g, w, "ORIG DOUBLE q1 solo")
                    drawFit(g, img("q1"), 0, 44, w, h - 44)
                }, koyuraLabel = "KOYURA SPREAD", origLabel = "ORIGINAL SPREAD (code-derived)",
            ),
            Shot(
                "webtoon-long", ComicReadingMode.WEBTOON, jumpPage = 3,
                orig = { g, w, h ->
                    caption(g, w, "ORIG WEBTOON q3 top")
                    drawFillWidthTop(g, img("q3"), 0, 44, w)
                }, koyuraLabel = "KOYURA LONG", origLabel = "ORIGINAL LONG (code-derived)",
            ),
            Shot(
                "pager-long-fitwidth", ComicReadingMode.LEFT_TO_RIGHT, fit = ComicScaleFit.FIT_WIDTH,
                jumpPage = 3,
                orig = { g, w, h ->
                    caption(g, w, "ORIG FIT_WIDTH q3 crop")
                    drawFillWidthCenterCrop(g, img("q3"), 0, 44, w, h - 44)
                }, koyuraLabel = "KOYURA FIT_WIDTH", origLabel = "ORIGINAL FIT_WIDTH (code-derived)",
            ),
            Shot(
                "failed", ComicReadingMode.LEFT_TO_RIGHT, jumpPage = 5,
                orig = { g, w, h ->
                    caption(g, w, "ORIG q5 error+Retry")
                    errorLayout(g, w, h)
                }, koyuraLabel = "KOYURA FAILED", origLabel = "ORIGINAL FAILED (code-derived)",
            ),
            Shot(
                "failed-retried", ComicReadingMode.LEFT_TO_RIGHT, jumpPage = 5,
                orig = { g, w, h ->
                    caption(g, w, "ORIG q5 retried")
                    drawFit(g, img("q5"), 0, 44, w, h - 44)
                }, koyuraLabel = "KOYURA RETRIED", origLabel = "ORIGINAL RETRIED (code-derived)",
            ),
        )
        for (shot in shots) {
            // The failed shot needs the first failure still present; the retried shot
            // loads through the real session retry like the Koyura screenshot test.
            val koyura = if (shot.name == "failed-retried") {
                koyuraRetried(shot)
            } else {
                koyuraBytesSequenced(
                    shot.mode, shot.layout, shot.invert, shot.fit,
                    jumps = shot.jumps, widthDp = shot.width, heightDp = shot.height,
                )
            }
            val orig = if (shot.width == 1280) {
                origWide { g, w, h -> shot.orig(g, w, h) }
            } else {
                origNarrow { g, w, h -> shot.orig(g, w, h) }
            }
            write("orig-${shot.name}", orig)
            // Koyura side is already covered by viewer-*.png; the sheet is the deliverable.
            write("compare-${shot.name}", sheet(koyura, orig, shot.koyuraLabel, shot.origLabel))
        }
    }

    private fun koyuraRetried(shot: Shot): ByteArray {
        val service = ViewerFixture.service()
        val session = ViewerFixture.session(service)
        runBlocking {
            session.open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds)
        }
        val options = ComicViewerOptions(
            mode = shot.mode,
            pagerLayout = shot.layout,
            firstPageSingle = true,
            invertDoublePages = shot.invert,
            scaleFit = shot.fit,
        )
        val jump = ComicViewerJump(requestId = 9205L, contentSeq = session.contentSeq, pageIndex = 5)
        // First pass: land on the failing page so the failure is recorded.
        renderOnce(session, options, jump, shot.width, shot.height)
        // Retry through the session between passes, like production would.
        runBlocking { session.retryPage("q5") }
        // Second pass: capture the retried page.
        return renderOnce(session, options, jump, shot.width, shot.height)
    }

    private fun renderOnce(
        session: ReaderSession,
        options: ComicViewerOptions,
        jump: ComicViewerJump?,
        widthDp: Int,
        heightDp: Int,
    ): ByteArray {
        val content = (session.content as? ReaderContentState.Ready)?.content
            ?: error("chapter not opened")
        val holder = arrayOf<ByteArray?>(null)
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(width = widthDp, height = heightDp, density = Density(1f)) {
                ReaderTheme(darkTheme = false) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        ComicViewer(
                            session = session,
                            content = content,
                            options = options,
                            jump = jump,
                            onToggleMenu = {},
                            onChapterBoundary = {},
                        )
                    }
                }
            }
            try {
                scene.render(2_000_000_000)
                scene.render(2_500_000_000)
                val image = scene.render(3_000_000_000)
                holder[0] = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            } finally {
                scene.close()
            }
        }
        return holder[0] ?: error("no render")
    }
}
