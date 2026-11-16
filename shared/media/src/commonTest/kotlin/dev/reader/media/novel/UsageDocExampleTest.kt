package dev.reader.media.novel

import dev.reader.media.epub.openEpubNovel
import dev.reader.media.fixtureRoot
import dev.reader.media.novel.txt.openTxtNovel
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Keeps `docs/usage.md` honest by reading the document at test time.
 *
 * Two independent checks, because neither alone is enough:
 * 1. [documentedExampleBlockMatchesExecutedCode] parses the first ```` ```kotlin ```` block out of the
 *    real file on disk and asserts the required API sequence is present. Editing the doc's calls
 *    without keeping that sequence fails here, so a doc-only change cannot silently drift.
 * 2. The tests below execute a transcription of the same example against real files. Because that
 *    transcription is compiled, an API change breaks the build. Unlike check 1, this one cannot notice
 *    a doc-only edit by itself, which is why both exist.
 *
 * The transcription deliberately substitutes a capture list for `println` and passes an injected
 * `FileSystem`; the call sequence and semantics are otherwise identical. The full doc block is not
 * evaluated as code (there is no runtime Kotlin compiler on the iOS simulator target).
 */
class UsageDocExampleTest {

    /** Structured capture of what the documented example prints. */
    private data class Observed(
        val printed: MutableList<String> = mutableListOf(),
        var resolvedResolution: String? = null,
        var resolvedChapter: String? = null,
    )

    /** Exactly the doc's minimal example body, with output captured instead of printed. */
    private fun readLocalBook(pathText: String, obs: Observed, fileSystem: FileSystem) {
        val path = pathText.toPath()

        // 1) 按扩展名选择导入器。二者返回同一个 NovelImport 契约。
        val novel: NovelImport =
            if (pathText.endsWith(".epub", ignoreCase = true)) {
                openEpubNovel(fileSystem, path)
            } else {
                openTxtNovel(fileSystem, path)
            }

        obs.printed += novel.metadata.title
        obs.printed += novel.metadata.authors.joinToString(",")
        obs.printed += novel.contentVersion

        val toc = novel.tableOfContents
        val firstChapter = toc.first { it.level == 1 }

        // 2) 读一章正文。
        val chapter = novel.readChapter(firstChapter.id)
        for (block in chapter.blocks) {
            when (block) {
                is NovelBlock.Heading -> obs.printed += "# ${block.text}"
                is NovelBlock.Paragraph -> obs.printed += block.text
                is NovelBlock.Separator -> obs.printed += "---"
                is NovelBlock.Image -> {
                    val resource = block.reference.resource
                    if (resource != null) {
                        val bytes = novel.readResource(resource.path)
                        obs.printed += "image ${resource.path}: ${bytes.size} bytes"
                    }
                }
            }
        }

        // 4) 保存阅读位置。
        val saved: ReadingPosition = novel.positionOf(firstChapter.id, blockIndex = 0, codePointOffset = 0)
        val json = kotlinx.serialization.json.Json.encodeToString(ReadingPosition.serializer(), saved)
        obs.printed += "position: $json"

        // 5) 重新打开后恢复（文档示例在同一个实例上恢复；这里同实例即满足语义）。
        val restored = kotlinx.serialization.json.Json.decodeFromString(ReadingPosition.serializer(), json)
        val resolution = novel.resolvePosition(restored)
        obs.resolvedResolution = resolution.resolution.name
        obs.resolvedChapter = resolution.position.chapterId
        obs.printed += "resolved to ${resolution.position.chapterId} block=${resolution.position.blockIndex} " +
            "offset=${resolution.position.codePointOffset} (${resolution.resolution})"

        // 6) 用完即关。
        novel.close()
    }

    @Test
    fun documentedExampleRunsAgainstRealTxt() {
        val path = fixtureRoot().toPath() / "txt" / "chapters-utf8.txt"
        val observed = Observed()
        readLocalBook(path.toString(), observed, FileSystem.SYSTEM)

        // Metadata and version were printed, and the version is a real fingerprint.
        assertTrue(observed.printed[0].isNotEmpty(), "title printed: ${observed.printed}")
        assertEquals(16, observed.printed[2].length)
        // The first level-1 entry of this fixture is the front matter ("长夜行"), so its own text must
        // appear verbatim in the printed chapter body.
        assertTrue(
            observed.printed.any { it == "长夜行" } && observed.printed.any { it == "作者：佚名" },
            "front-matter paragraphs printed: ${observed.printed}",
        )
        // The round trip resolved exactly to the same chapter we saved from.
        assertEquals("EXACT", observed.resolvedResolution)
        assertTrue(observed.printed.any { it.startsWith("resolved to ") })
    }

    @Test
    fun documentedExampleRunsAgainstRealEpubWithImages() {
        val path = fixtureRoot().toPath() / "epub" / "cole-voyage-of-life.epub"
        val observed = Observed()
        readLocalBook(path.toString(), observed, FileSystem.SYSTEM)

        assertEquals("Thomas Cole - The Voyage of Life", observed.printed[0])
        assertTrue(observed.printed[1].contains("Jesse Dylan"))
        assertEquals(16, observed.printed[2].length)
        // The example's image branch ran: at least one "image …: N bytes" line exists.
        assertTrue(
            observed.printed.any { it.startsWith("image ") && it.endsWith(" bytes") },
            "no image line was produced: ${observed.printed}",
        )
        assertEquals("EXACT", observed.resolvedResolution)
    }

    /** Reads `docs/usage.md` from disk and validates its minimal-example block. */
    @Test
    fun documentedExampleBlockMatchesExecutedCode() {
        // fixtureRoot() is <module>/fixtures; docs is its sibling.
        val docPath = fixtureRoot().toPath().parent!! / "docs" / "usage.md"
        assertTrue(FileSystem.SYSTEM.exists(docPath), "missing doc: $docPath")
        val markdown = FileSystem.SYSTEM.read(docPath) { readUtf8() }
        val block = firstKotlinBlock(markdown)
            ?: error("docs/usage.md has no ```kotlin block")

        // The example must be a self-contained function with this exact signature.
        assertTrue(
            block.contains("fun readLocalBook(pathText: String)"),
            "doc example signature changed; update the test transcription too",
        )
        // Required API sequence, in order. If the doc drops or renames any of these, this fails.
        val requiredInOrder = listOf(
            "openEpubNovel(FileSystem.SYSTEM, path)",
            "openTxtNovel(FileSystem.SYSTEM, path)",
            "novel.metadata.title",
            "novel.metadata.authors",
            "novel.contentVersion",
            "val toc = novel.tableOfContents",
            "val firstChapter = toc.first { it.level == 1 }",
            "novel.readChapter(firstChapter.id)",
            "is NovelBlock.Heading",
            "is NovelBlock.Paragraph",
            "is NovelBlock.Separator",
            "is NovelBlock.Image",
            "novel.readResource(resource.path)",
            "novel.positionOf(firstChapter.id, blockIndex = 0, codePointOffset = 0)",
            "Json.encodeToString(ReadingPosition.serializer(), saved)",
            "Json.decodeFromString(ReadingPosition.serializer(), json)",
            "novel.resolvePosition(restored)",
            "novel.close()",
        )
        var cursor = 0
        for (required in requiredInOrder) {
            val found = block.indexOf(required, cursor)
            assertTrue(found >= 0, "doc example is missing (or reordered) `$required`")
            cursor = found + required.length
        }
        // The doc must not resurrect the removed TXT image-bytes claim: TXT has no resources.
        assertTrue(
            !block.contains("readResource", ignoreCase = false) || block.contains("if (resource != null)"),
            "image reads must stay guarded by a null check",
        )
    }

    /**
     * Returns the contents of the first fenced ```kotlin block that starts on its own line.
     *
     * The check for a line start matters: prose in the same file mentions ```kotlin inside a sentence,
     * and a naive `indexOf` would match that mention instead of the real fence.
     */
    private fun firstKotlinBlock(markdown: String): String? {
        val fence = Regex("(?m)^```kotlin[ \t]*$")
        val opening = fence.find(markdown) ?: return null
        val start = markdown.indexOf('\n', opening.range.last)
        if (start < 0) return null
        val closing = Regex("(?m)^```[ \t]*$").find(markdown, start + 1) ?: return null
        return markdown.substring(start + 1, closing.range.first)
    }

    @Test
    fun documentedUseImportSnippetClosesEvenOnException() {
        // Mirrors the doc's `useImport { … }` snippet, including the exception guarantee.
        val path = fixtureRoot().toPath() / "txt" / "sample-utf8.txt"
        var captured: String? = null
        openTxtNovel(FileSystem.SYSTEM, path).useImport { novel ->
            captured = novel.metadata.title
        }
        assertEquals("sample-utf8", captured)

        val failing = openTxtNovel(FileSystem.SYSTEM, path)
        val chapterId = failing.tableOfContents.single().id
        kotlin.test.assertFailsWith<IllegalStateException> {
            failing.useImport { novel ->
                novel.readChapter(chapterId)
                error("boom")
            }
        }
        kotlin.test.assertFailsWith<NovelClosedException> { failing.readChapter(chapterId) }
    }
}
