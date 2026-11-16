package dev.reader.media.epub

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import dev.reader.media.novel.NovelImportException

/**
 * Parses an EPUB 3 nav document into normalized navigation entries.
 *
 * Selection rules:
 * - only a `<nav>` whose type token list contains `toc` is used. A nav document whose only navs are
 *   `landmarks`/`page-list` yields no entries, letting the caller fall back to the spine rather than
 *   presenting landmarks as a table of contents;
 * - the type attribute is matched on **local name** `type`, so any prefix bound to the OPS namespace
 *   works (`epub:type`, `ops:type`, an unprefixed `type`). EPUB permits any prefix, not just `epub`.
 *
 * Structure rules:
 * - `ol`, `ul` and `dl`-style lists are traversed; a list nested inside `div` or other wrappers is
 *   found by recursion;
 * - an `<li>` at the top level whose text has no anchor is a grouping label such as `Volume I` and
 *   becomes level 0; every anchor resolving to a reading-spine document becomes level 1.
 */
internal object EpubNavParser {

    /**
     * A nav anchor resolved to an archive document.
     *
     * [groupPath] is the chain of enclosing grouping labels, outermost first (for example
     * `["Volume I"]` or `["Part One", "Section A"]`). An entry with an empty chain is a top-level
     * chapter. This models hierarchy by *nesting*, so two different groups that happen to share a
     * title stay distinct and a chapter outside any group never inherits one.
     */
    data class NavEntry(
        val path: String,
        val fragment: String?,
        val title: String,
        /** Level derived from grouping, `0` for volume labels and `1` for readable anchors. */
        val level: Int,
        val groupPath: List<NavGroup> = emptyList(),
    )

    /**
     * One grouping label on the way to an entry.
     *
     * [ordinal] is unique within the document, so two sibling groups that happen to share a title stay
     * distinct (a `Volume I` before and after a reset, or duplicated `Part` labels).
     */
    data class NavGroup(
        val ordinal: Int,
        val title: String,
    )

    /** List containers accepted at any nesting depth. */
    private val LIST_TAGS = setOf("ol", "ul", "menu", "dl")

    /**
     * Elements whose subtree never contains a navigation list.
     *
     * Only true text-only wrappers are skipped. `header`/`section`/`div` are NOT skipped: a TOC list
     * is sometimes wrapped in them, and skipping would drop the entire table of contents.
     */
    private val IGNORED_NAV_TAGS = setOf("script", "style", "title")

    fun parse(
        archivePath: String,
        navPath: String,
        navXml: String,
        readableDocuments: Set<String>,
    ): List<NavEntry> {
        val document = try {
            Ksoup.parseXml(navXml)
        } catch (e: Throwable) {
            throw NovelImportException.CorruptedArchive(
                path = archivePath,
                entry = navPath,
                detail = "invalid-nav",
                cause = e,
            )
        }

        // Select strictly by toc type; never fall back to an arbitrary nav (landmarks, page-list).
        val nav = document.select("nav").firstOrNull { it.hasTypeToken("toc") } ?: return emptyList()

        val entries = mutableListOf<NavEntry>()

        // Recursive local functions must be declared as a pair; Kotlin cannot forward-reference a
        // local fun, so visitItem is an object-level helper receiving the mutable list instead.
        val context = ParseContext(archivePath, navPath, readableDocuments, entries)
        // Collect every top-level list in the nav, whether a direct child or wrapped in a
        // div/section. A list nested inside another list is not a root: its parent list already visits
        // it, and treating it as a root would duplicate or misorder entries.
        val roots = mutableListOf<Element>()
        fun collectRoots(node: Element) {
            for (child in node.children()) {
                val name = child.tagName().lowercase()
                when {
                    name in LIST_TAGS -> roots.add(child)
                    name in IGNORED_NAV_TAGS -> Unit
                    else -> collectRoots(child)
                }
            }
        }
        collectRoots(nav)
        for (root in roots) {
            visitList(root, depth = 0, context)
        }
        return entries
    }

    private class ParseContext(
        val archivePath: String,
        val navPath: String,
        val readableDocuments: Set<String>,
        val entries: MutableList<NavEntry>,
        /** Shared counter so every grouping label gets a distinct ordinal. */
        var nextGroupOrdinal: Int = 0,
    )

    private fun visitList(
        list: Element,
        depth: Int,
        context: ParseContext,
        groupPath: List<NavGroup> = emptyList(),
    ) {
        for (child in list.children()) {
            val name = child.tagName().lowercase()
            when {
                name == "li" -> visitItem(child, depth, context, groupPath)
                name in LIST_TAGS -> visitList(child, depth, context, groupPath)
                else -> visitList(child, depth, context, groupPath)
            }
        }
    }

    private fun visitItem(
        item: Element,
        depth: Int,
        context: ParseContext,
        groupPath: List<NavGroup>,
    ) {
        // Look for an anchor that belongs to *this* item, not to a nested list's item. Descending
        // search would attribute a child chapter's link to its parent group label.
        val ownAnchor = firstOwnAnchor(item)
        val childLists = childListsOf(item)

        if (ownAnchor != null) {
            val href = ownAnchor.attr("href").trim()
            val title = ownAnchor.text().trim()
            if (href.isNotEmpty() && title.isNotEmpty()) {
                val resolved = resolve(context.archivePath, context.navPath, href)
                if (resolved != null && resolved.path in context.readableDocuments) {
                    context.entries += NavEntry(
                        path = resolved.path,
                        fragment = resolved.fragment,
                        title = title,
                        level = 1,
                        groupPath = groupPath,
                    )
                }
            }
        }

        // A grouping label is any item with no own anchor but with nested lists; it extends the group
        // path for its children. It is emitted once so the group can appear in the TOC.
        val ownLabel = if (ownAnchor == null && childLists.isNotEmpty()) item.ownLabel() else ""
        val childGroupPath = if (ownLabel.isNotEmpty()) {
            val group = NavGroup(ordinal = context.nextGroupOrdinal++, title = ownLabel)
            context.entries += NavEntry(
                path = "",
                fragment = null,
                title = ownLabel,
                level = 0,
                groupPath = groupPath,
            )
            groupPath + group
        } else {
            groupPath
        }

        for (child in childLists) visitList(child, depth + 1, context, childGroupPath)
    }

    /**
     * Finds the anchor owned by [item], skipping anchors nested inside its child lists.
     *
     * Returns `null` when every anchor belongs to a descendant list, which is how a group label such
     * as `<li><span>Part</span><ol><li><a>One</a></li></ol></li>` is distinguished from a real entry.
     */
    private fun firstOwnAnchor(item: Element): Element? {
        val queue = ArrayDeque<Element>()
        for (child in item.children()) {
            if (child.tagName().lowercase() in LIST_TAGS) continue
            queue.addLast(child)
        }
        while (queue.isNotEmpty()) {
            val element = queue.removeFirst()
            if (element.tagName().equals("a", ignoreCase = true)) return element
            if (element.tagName().lowercase() in LIST_TAGS) continue
            for (child in element.children()) {
                if (child.tagName().lowercase() !in LIST_TAGS) queue.addLast(child)
            }
        }
        return null
    }

    /** Direct `<ol>`/`<ul>` children, or those wrapped in one non-list element. */
    private fun childListsOf(item: Element): List<Element> =
        item.children().filter { it.tagName().lowercase() in LIST_TAGS }
            .ifEmpty {
                item.children().flatMap { wrapper ->
                    if (wrapper.tagName().lowercase() in LIST_TAGS) emptyList()
                    else wrapper.children().filter { it.tagName().lowercase() in LIST_TAGS }
                }
            }

    /** `true` when the element's `type` attribute (any prefix) contains [token]. */
    private fun Element.hasTypeToken(token: String): Boolean {
        // Ksoup keeps prefixes literally, so read every attribute whose local name is `type`.
        val attributes = attributes().asList()
        for (attribute in attributes) {
            val key = attribute.key
            if (localName(key) != "type") continue
            val value = attribute.value
            if (value.split(' ', '\t', '\n').any { it.equals(token, ignoreCase = true) }) return true
        }
        return false
    }

    private fun localName(name: String): String {
        val colon = name.lastIndexOf(':')
        return if (colon >= 0) name.substring(colon + 1) else name
    }

    private fun resolve(archivePath: String, navPath: String, href: String): EpubPathResolver.Resolved? =
        try {
            EpubPathResolver.resolve(archivePath, navPath, href)
        } catch (_: NovelImportException.UnsafeReference) {
            null
        }

    /** Text of an `<li>` before its nested list, used as a group label. */
    private fun Element.ownLabel(): String {
        val builder = StringBuilder()
        for (node in childNodes()) {
            if (node is Element && node.tagName().lowercase() in LIST_TAGS) continue
            builder.append(nodeText(node))
        }
        return builder.toString().trim()
    }

    private fun nodeText(node: Node): String = when (node) {
        is TextNode -> node.text()
        is Element -> node.text()
        else -> ""
    }
}
