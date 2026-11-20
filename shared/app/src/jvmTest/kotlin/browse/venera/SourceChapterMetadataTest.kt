package app.koyura.app.browse.venera

import app.koyura.app.browse.WorkChapter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class SourceChapterMetadataTest {
    @Test
    fun readsFlatGroupedAndEnrichedCatalogsInSourceOrder() = runTest {
        val fixture = VeneraHarness()
        try {
            fixture.service.open()
            fixture.service.installFromContent("flat.js", source("flat", """{a: "One", b: "Two"}"""))
            fixture.service.installFromContent("grouped.js", source("grouped",
                """{volume1: {a: "One"}, volume2: {b: "Two"}}"""))
            fixture.service.installFromContent("metadata.js", source("metadata", """
                {
                    a: {title: "One", dateMillis: 1700000000000, scanlator: "Team A", pageCount: 19},
                    volume2: {
                        b: {title: "Two", uploadTime: "2025-03-04T01:02:03Z", scanlator: " ", pageCount: -1},
                        c: {title: "Three", date: "2025-03-05", pageCount: "21"},
                        d: {title: "Four", date: "unknown"},
                        e: "Five"
                    }
                }
            """))
            val browse = fixture.browse()
            val oldCatalog = listOf(WorkChapter("a", "One"), WorkChapter("b", "Two"))
            assertEquals(oldCatalog, browse.loadWorkDetail("flat", "comic").chapters)
            assertEquals(oldCatalog, browse.loadWorkDetail("grouped", "comic").chapters)
            assertEquals(
                listOf(
                    WorkChapter("a", "One", 1700000000000, "Team A", 19),
                    WorkChapter("b", "Two", Instant.parse("2025-03-04T01:02:03Z").toEpochMilliseconds()),
                    WorkChapter("c", "Three", Instant.parse("2025-03-05T00:00:00Z").toEpochMilliseconds(), pageCount = 21),
                    WorkChapter("d", "Four"),
                    WorkChapter("e", "Five"),
                ),
                browse.loadWorkDetail("metadata", "comic").chapters,
            )
        } finally {
            fixture.close()
        }
    }

    private fun source(key: String, catalog: String) = """
        class Source extends ComicSource {
            name = "$key"; key = "$key"; version = "1.0.0"; minAppVersion = "1.0.0";
            comic = {loadInfo: () => ({title: "Comic", chapters: $catalog})};
        }
    """.trimIndent()
}
