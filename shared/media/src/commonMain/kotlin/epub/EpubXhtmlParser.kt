package app.koyura.media.epub

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import app.koyura.media.novel.ImageReference
import app.koyura.media.novel.NovelBlock
import app.koyura.media.novel.NovelImportException
import app.koyura.media.novel.NovelResource
import app.koyura.media.novel.SeparatorStyle

/**
 * Converts one XHTML document into [NovelBlock]s and, optionally, a fallback title.
 *
 * Mapping:
 * - `h1`..`h6` → [NovelBlock.Heading] with the matching level;
 * - `p` and block-level containers → [NovelBlock.Paragraph], with inline text flattened;
 * - `hr` → [NovelBlock.Separator] with [SeparatorStyle.DASHES];
 * - local `img` → [NovelBlock.Image] whose reference carries a normalized, readable resource;
 * - `script`, `style`, `head`, and remote images are skipped, never fetched.
 *
 * Inline traversal keeps text order, drops `script`/`style` subtrees, and inserts a space at `<br>`
 * so `line1<br/>line2` does not become `line1line2`. Words are **not** re-spaced between inline
 * elements beyond what the document already contains, because EPUB XHTML already has correct
 * whitespace and CJK text must not gain spaces.
 */
internal object EpubXhtmlParser {

    /** Elements whose subtrees never contribute reading content. */
    private val IGNORED_ELEMENTS = setOf("script", "style", "head", "title", "meta", "link")

    /**
     * Inline elements that continue the surrounding paragraph rather than starting a new block.
     *
     * Keeping this list explicit (instead of "anything unknown is inline") means a genuinely unknown
     * block element still ends a paragraph, so structure is never lost for new container tags.
     */
    private val INLINE_ELEMENTS = setOf(
        "a", "span", "em", "i", "strong", "b", "abbr", "cite", "q", "small", "sub", "sup",
        "code", "kbd", "samp", "var", "mark", "s", "u", "bdi", "bdo", "time", "data", "dfn",
        // Ruby annotations are inline in Japanese/Chinese EPUBs; without these a single sentence is
        // split into base / reading / tail paragraphs.
        "ruby", "rt", "rp", "rb", "rtc",
    )

    /** Result of converting a chapter document. */
    data class ParsedDocument(
        val blocks: List<NovelBlock>,
        /** Document `<title>`, or the first heading text when no title exists. */
        val fallbackTitle: String?,
    )

    /**
     * Converts [xhtml] belonging to [documentPath].
     *
     * Image references are resolved through [EpubPathResolver]; a remote or otherwise unsafe `src`
     * yields an [ImageReference] with `resource == null` and the raw source preserved, so the reader
     * can hide it without the importer ever fetching anything.
     */
    fun parse(archivePath: String, documentPath: String, xhtml: String): ParsedDocument {
        val document = try {
            Ksoup.parse(xhtml)
        } catch (e: Throwable) {
            // Defensive: Ksoup is very tolerant, but a broken document must not abort reading.
            return ParsedDocument(blocks = emptyList(), fallbackTitle = null)
        }
        val body = document.body()
        val blocks = mutableListOf<NovelBlock>()
        if (body != null) {
            appendBlocks(archivePath, documentPath, body, blocks)
        }
        val fallbackTitle = document.title().trim().takeIf { it.isNotEmpty() }
            ?: blocks.filterIsInstance<NovelBlock.Heading>().firstOrNull()?.text
        return ParsedDocument(blocks = blocks, fallbackTitle = fallbackTitle)
    }

    /**
     * Walks a block container, preserving both element children and bare text.
     *
     * XHTML permits loose text inside `div`/`section`/`blockquote`/`figcaption`, and that text often
     * carries inline markup (`<div>She said <em>no</em> loudly.</div>`). Inline elements therefore
     * contribute their flattened text to the **same** paragraph; only block-level elements end a
     * paragraph. Without this distinction a single sentence would be split into one paragraph per
     * inline element.
     *
     * Block boundaries are: paragraphs, headings, lists, tables, `hr`, `img` as a standalone block, and
     * any nested block container. `script`/`style`/`head` subtrees never contribute.
     */
    private fun appendBlocks(
        archivePath: String,
        documentPath: String,
        parent: Element,
        blocks: MutableList<NovelBlock>,
    ) {
        val buffer = StringBuilder()

        fun flushText() {
            val text = buffer.toString().trim()
            if (text.isNotEmpty()) blocks += NovelBlock.Paragraph(text)
            buffer.clear()
        }

        for (node in parent.childNodes()) {
            when (node) {
                is TextNode -> buffer.append(node.text())
                is Element -> {
                    val name = node.tagName().lowercase()
                    if (name in IGNORED_ELEMENTS) continue
                    when {
                        name == "br" -> buffer.append(' ')
                        // A block-level wrapper such as `<a href="x"><img/></a>` must still produce an
                        // image: walking it as plain inline text would keep only the alt string. The
                        // paragraph walker emits text and images in order.
                        name in INLINE_ELEMENTS && containsImage(node) -> {
                            flushText()
                            appendParagraph(archivePath, documentPath, node, blocks)
                        }
                        // Otherwise inline elements stay inside the current paragraph.
                        name in INLINE_ELEMENTS -> appendInlineText(node, buffer)
                        name == "img" -> {
                            flushText()
                            imageBlock(archivePath, documentPath, node)?.let { blocks += it }
                        }
                        else -> {
                            flushText()
                            appendBlockElement(archivePath, documentPath, node, blocks)
                        }
                    }
                }
                else -> Unit
            }
        }
        flushText()
    }

    private fun appendBlockElement(
        archivePath: String,
        documentPath: String,
        child: Element,
        blocks: MutableList<NovelBlock>,
    ) {
        when (val name = child.tagName().lowercase()) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val text = inlineText(child).trim()
                if (text.isNotEmpty()) {
                    blocks += NovelBlock.Heading(level = name[1].digitToInt(), text = text)
                }
            }
            "hr" -> blocks += NovelBlock.Separator(SeparatorStyle.DASHES)
            "img" -> imageBlock(archivePath, documentPath, child)?.let { blocks += it }
            "p" -> appendParagraph(archivePath, documentPath, child, blocks)
            "ul", "ol", "dl" -> appendListItems(archivePath, documentPath, child, blocks)
            "table" -> appendTableRows(archivePath, documentPath, child, blocks)
            "br" -> Unit
            else -> {
                // Container or unknown element: recurse so inner text and blocks survive.
                if (child.children().isEmpty() && !child.hasText()) {
                    Unit
                } else {
                    appendBlocks(archivePath, documentPath, child, blocks)
                }
            }
        }
    }

    /**
     * Flattens a paragraph, emitting an [NovelBlock.Image] for every `<img>` it contains **anywhere
     * inside** it, including images wrapped in `<a>`, `<span>`, `<em>` and similar inline elements.
     *
     * EPUBs routinely write `<p><a href="big.jpg"><img src="small.jpg"/></a></p>`; walking only the
     * direct children would lose the image and leave only its alt text. Text around each image stays
     * in order, and a paragraph that is only an image becomes only an image block.
     */
    private fun appendParagraph(
        archivePath: String,
        documentPath: String,
        paragraph: Element,
        blocks: MutableList<NovelBlock>,
    ) {
        val buffer = StringBuilder()

        fun flushText() {
            val text = buffer.toString().trim()
            if (text.isNotEmpty()) blocks += NovelBlock.Paragraph(text)
            buffer.clear()
        }

        fun walk(node: Node) {
            when (node) {
                is TextNode -> buffer.append(node.text())
                is Element -> when (node.tagName().lowercase()) {
                    "script", "style" -> Unit
                    "img" -> {
                        flushText()
                        imageBlock(archivePath, documentPath, node)?.let { blocks += it }
                    }
                    "br" -> buffer.append(' ')
                    "hr" -> {
                        flushText()
                        blocks += NovelBlock.Separator(SeparatorStyle.DASHES)
                    }
                    else -> for (child in node.childNodes()) walk(child)
                }
                else -> Unit
            }
        }

        for (node in paragraph.childNodes()) walk(node)
        flushText()
    }

    private fun appendListItems(
        archivePath: String,
        documentPath: String,
        list: Element,
        blocks: MutableList<NovelBlock>,
    ) {
        val items = list.children().filter { it.tagName().equals("li", ignoreCase = true) }
        if (items.isEmpty()) {
            appendBlocks(archivePath, documentPath, list, blocks)
            return
        }
        for (item in items) {
            appendParagraph(archivePath, documentPath, item, blocks)
        }
    }

    private fun appendTableRows(
        archivePath: String,
        documentPath: String,
        table: Element,
        blocks: MutableList<NovelBlock>,
    ) {
        val rows = table.select("tr").filter { it.parents().none { p -> p.tagName().equals("table", true) && p !== table } }
        if (rows.isEmpty()) {
            val text = inlineText(table).trim()
            if (text.isNotEmpty()) blocks += NovelBlock.Paragraph(text)
            return
        }
        for (row in rows) {
            val cells = row.children().filter { it.tagName().equals("td", true) || it.tagName().equals("th", true) }
            val text = cells.joinToString(separator = " ") { inlineText(it).trim() }.trim()
            if (text.isNotEmpty()) blocks += NovelBlock.Paragraph(text)
        }
    }

    private fun imageBlock(archivePath: String, documentPath: String, image: Element): NovelBlock.Image? {
        val rawSrc = image.attr("src").trim()
        if (rawSrc.isEmpty()) return null
        val alt = image.attr("alt").trim().takeIf { it.isNotEmpty() }
        if (EpubPathResolver.isRemote(rawSrc)) {
            // Remote image: keep the raw reference but never expose a readable resource.
            return NovelBlock.Image(ImageReference(source = rawSrc, altText = alt, resource = null))
        }
        val path = try {
            EpubPathResolver.resolve(archivePath, documentPath, rawSrc).path
        } catch (_: NovelImportException.UnsafeReference) {
            // Escape attempt: keep the raw text, expose no resource, do not fail the whole chapter.
            return NovelBlock.Image(ImageReference(source = rawSrc, altText = alt, resource = null))
        }
        return NovelBlock.Image(
            ImageReference(
                source = rawSrc,
                altText = alt,
                resource = NovelResource(path = path, mediaType = null, altText = alt),
            ),
        )
    }

    /**
     * Flattens inline content of [element] to text.
     *
     * `script`/`style` subtrees are dropped, `<br>` becomes a space, and an `<img>` contributes its
     * alt text (never a URL) so a caption-like image inside a paragraph is not silently lost.
     */
    private fun inlineText(element: Element): String {
        val builder = StringBuilder()
        appendInlineText(element, builder)
        return builder.toString()
    }

    /**
     * `true` when [element] or any descendant is an `<img>`.
     *
     * Used to decide that an inline wrapper should be walked as a paragraph so its images survive,
     * which is what makes `<div><a href="big"><img src="cover.jpg"/></a></div>` produce an image.
     */
    private fun containsImage(element: Element): Boolean {
        for (child in element.children()) {
            val name = child.tagName().lowercase()
            if (name == "img") return true
            if (name in IGNORED_ELEMENTS) continue
            if (containsImage(child)) return true
        }
        return false
    }

    /**
     * Appends flattened inline text of [element], skipping script/style and using alt for images.
     *
     * `<rp>` holds fallback parentheses for readers without ruby support; its text is dropped so a
     * ruby annotation does not leak `(`/`)` into the paragraph. `<rt>` text is kept, so the reading is
     * preserved next to its base.
     */
    private fun appendInlineText(element: Element, builder: StringBuilder) {
        for (node in element.childNodes()) {
            when (node) {
                is TextNode -> builder.append(node.text())
                is Element -> when (node.tagName().lowercase()) {
                    "script", "style", "rp" -> Unit
                    "br" -> builder.append(' ')
                    "hr" -> Unit
                    "img" -> {
                        val alt = node.attr("alt").trim()
                        if (alt.isNotEmpty()) builder.append(alt)
                    }
                    else -> appendInlineText(node, builder)
                }
                else -> Unit
            }
        }
    }
}
