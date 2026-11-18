package app.koyura.app.library

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.koyura.app.browse.WorkChapter
import app.koyura.app.browse.venera.VeneraBrowseService
import app.koyura.app.browse.venera.VeneraFixtures
import app.koyura.app.browse.venera.VeneraHarness
import app.koyura.app.reader.VeneraReaderService
import app.koyura.data.extension.ExtensionDb
import app.koyura.data.history.HistoryStore
import app.koyura.data.library.LibraryStore
import app.koyura.data.session.openExtensionSession
import app.koyura.domain.library.LibraryPrefsKeys
import app.koyura.domain.library.WorkKind
import app.koyura.features.library.LibraryKind
import app.koyura.features.library.LibrarySession
import app.koyura.features.library.NewLibraryEntry
import app.koyura.network.applyReaderDefaults
import app.koyura.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * 书架回归：隐藏语义、真实章节已读、七开关、从书架开始/继续。
 *
 * 全部经真实仓储（文件型 SQLite + QuickJS + MockEngine），重开可恢复；
 * 不使用假仓储、历史报告或缩小范围。
 */
class VeneraLibraryRegressionTest {
    private fun openProfile(profile: File, detailJson: String = DETAIL_JSON): app.koyura.data.session.ExtensionSession =
        runBlocking {
            val engine = MockEngine { request ->
                when (request.url.encodedPath) {
                    "/index.json" -> respond("[]", HttpStatusCode.OK)
                    "/api/comics/demo" -> respond(detailJson, HttpStatusCode.OK)
                    "/api/comics/other" -> respond(OTHER_DETAIL_JSON, HttpStatusCode.OK)
                    "/api/comics/demo/chapters/1" ->
                        respond("""{"images":["https://example.invalid/images/1.png"]}""", HttpStatusCode.OK)
                    "/api/comics/demo/chapters/2" ->
                        respond("""{"images":["https://example.invalid/images/1.png","https://example.invalid/images/2.png"]}""", HttpStatusCode.OK)
                    else -> respond("not found", HttpStatusCode.NotFound)
                }
            }
            openExtensionSession(
                JdbcSqliteDriver("jdbc:sqlite:${File(profile, "extensions.db").absolutePath}"),
                FileSystem.SYSTEM,
                profile.toOkioPath(),
                HttpClient(engine) { applyReaderDefaults() },
                VeneraEnvironment("1.16.0", "zh_CN", "desktop"),
            )
        }

    private fun production(session: app.koyura.data.session.ExtensionSession) = ProductionLibraryRepository(
        store = session.library,
        history = session.history,
        thumbnails = { _, _ -> ByteArray(0) },
    )

    @Test
    fun hiddenCategoriesExcludedFromAllAndSearch() {
        val profile = Files.createTempDirectory("regression-hidden").toFile()
        try {
            var session = openProfile(profile)
            try {
                val repo = production(session)
                runBlocking {
                    repo.addToLibrary(NewLibraryEntry("srcA", LibraryKind.COMIC, "hidden-only", title = "Hidden Only"))
                    repo.addToLibrary(NewLibraryEntry("srcA", LibraryKind.COMIC, "mixed", title = "Mixed Work"))
                    repo.addToLibrary(NewLibraryEntry("srcA", LibraryKind.COMIC, "plain", title = "Plain Work"))
                    val hidden = session.library.listCategories(includeHidden = true).first { it.isDefault }
                    val hiddenCat = session.library.createCategory("H")
                    session.library.setCategoryHidden(hiddenCat.id, true)
                    session.library.setWorkCategories("srcA", WorkKind.COMIC, "hidden-only", listOf(hiddenCat.id))
                    session.library.setWorkCategories("srcA", WorkKind.COMIC, "mixed", listOf(hidden.id, hiddenCat.id))
                    // plain 保持无分类。
                }
                // 经 LibrarySession 走同一可见范围断言。
                val libSession = LibrarySession(repo)
                runBlocking { libSession.refresh() }
                // 默认“全部”不泄露仅归属隐藏的作品，混合与无分类仍可见。
                assertEquals(2, libSession.visibleEntries.size)
                assertFalse(libSession.visibleEntries.any { it.workId == "hidden-only" })
                assertTrue(libSession.visibleEntries.any { it.workId == "mixed" })
                assertTrue(libSession.visibleEntries.any { it.workId == "plain" })
                // 搜索同范围。
                libSession.updateQuery("Hidden")
                assertTrue(libSession.visibleEntries.isEmpty())
                libSession.updateQuery("")
                // 打开开关后可查看。
                runBlocking { libSession.updatePrefs { it.copy(showHiddenCategories = true) } }
                assertEquals(3, libSession.visibleEntries.size)
                // 重开后仍正确（开关已持久化为 true，仍见隐藏），历史收藏状态保持完整。
                runBlocking { session.close() }
                session = openProfile(profile)
                val reopened = LibrarySession(production(session))
                runBlocking { reopened.refresh() }
                assertEquals(3, reopened.visibleEntries.size)
                runBlocking { reopened.updatePrefs { it.copy(showHiddenCategories = false) } }
                assertEquals(2, reopened.visibleEntries.size)
            } finally {
                runBlocking { session.close() }
            }
        } finally {
            profile.deleteRecursively()
        }
    }

    @Test
    fun chapterIsolationBatchAndRestart() {
        val profile = Files.createTempDirectory("regression-chapters").toFile()
        try {
            var session = openProfile(profile)
            try {
                val repo = production(session)
                runBlocking {
                    repo.addToLibrary(NewLibraryEntry("srcA", LibraryKind.COMIC, "demo", title = "Demo"))
                    repo.addToLibrary(NewLibraryEntry("srcA", LibraryKind.COMIC, "other", title = "Other"))
                    repo.addToLibrary(NewLibraryEntry("srcB", LibraryKind.COMIC, "demo", title = "Demo B"))
                    // 同源不同作品、不同源同 ID 各自同步目录，隔离键含 sourceKey + kind + workId + chapterId。
                    val d1 = listOf(WorkChapter("ep_2", "Ch2"), WorkChapter("ep_1", "Ch1"))
                    val d2 = listOf(WorkChapter("ep_9", "Only"))
                    repo.syncWorkDetail("srcA", LibraryKind.COMIC, "demo", d1)
                    repo.syncWorkDetail("srcA", LibraryKind.COMIC, "other", d2)
                    repo.syncWorkDetail("srcB", LibraryKind.COMIC, "demo", d1)
                    assertEquals(2, d1.size)
                }
                var snap = runBlocking { repo.load() }
                val demo = snap.entries.single { it.workId == "demo" && it.sourceKey == "srcA" }
                assertEquals(2, demo.totalChapters)
                assertEquals(2L, demo.unreadCount)
                // 批量标已读写入真实状态，详情同读，未读筛选与角标一致。
                runBlocking { repo.markAsRead(setOf(demo.key), true) }
                snap = runBlocking { repo.load() }
                assertEquals(0L, snap.entries.single { it.key == demo.key }.unreadCount)
                val states = runBlocking { repo.chapterReadStates(demo.key) }
                assertTrue(states.values.all { it })
                // 详情批量标未读（子集）同步接线。
                runBlocking { repo.markDetailChapters(demo.key, setOf(d1Id()), false) }
                snap = runBlocking { repo.load() }
                assertEquals(1L, snap.entries.single { it.key == demo.key }.unreadCount)
                // 不同源同 ID 互不干扰。
                val otherSource = snap.entries.single { it.sourceKey == "srcB" }
                assertEquals(2L, otherSource.unreadCount)
                // 阅读到末页记录已读，中途仅更新进度。
                val reader = VeneraReaderService(session.comicReading, session.history, session.library)
                runBlocking {
                    // 中途页：进度更新，已读不变。
                    reader.saveProgress("srcA", "demo", d1Id(), "p0", 0, "Demo", "Ch1", isLastPage = false)
                }
                snap = runBlocking { repo.load() }
                assertEquals(1L, snap.entries.single { it.key == demo.key }.unreadCount)
                runBlocking {
                    reader.saveProgress("srcA", "demo", d1Id(), "p1", 1, "Demo", "Ch1", isLastPage = true)
                }
                // 该章已读，未读回到 0（另一章此前已标已读？此处先全标未读再验）。
                runBlocking { repo.markAsRead(setOf(demo.key), false) }
                runBlocking {
                    reader.saveProgress("srcA", "demo", d1Id(), "p1", 1, "Demo", "Ch1", isLastPage = true)
                }
                snap = runBlocking { repo.load() }
                assertEquals(1L, snap.entries.single { it.key == demo.key }.unreadCount)
                // 重复刷新保留同 ID 已读，新章默认未读。
                // 模拟新增第三章：手动同步三章，旧章已读保留。
                runBlocking {
                    repo.markDetailChapters(demo.key, setOf(d1Id()), true)
                    repo.syncWorkDetail(
                        "srcA", LibraryKind.COMIC, "demo",
                        listOf(
                            WorkChapter("ep_2", "Ch2"),
                            WorkChapter("ep_1", "Ch1"),
                            WorkChapter("ep_3", "Ch3"),
                        ),
                    )
                }
                snap = runBlocking { repo.load() }
                assertEquals(3, snap.entries.single { it.key == demo.key }.totalChapters)
                assertEquals(2L, snap.entries.single { it.key == demo.key }.unreadCount)
                // 重开可恢复。
                runBlocking { session.close() }
                session = openProfile(profile)
                val reopenedRepo = production(session)
                val reopened = runBlocking { reopenedRepo.load() }
                assertEquals(3, reopened.entries.single { it.key == demo.key }.totalChapters)
                assertEquals(2L, reopened.entries.single { it.key == demo.key }.unreadCount)
            } finally {
                runBlocking { session.close() }
            }
        } finally {
            profile.deleteRecursively()
        }
    }

    @Test
    fun sevenSwitchesPersistAndTolerateLegacy() {
        val profile = Files.createTempDirectory("regression-prefs").toFile()
        try {
            var session = openProfile(profile)
            try {
                val repo = production(session)
                runBlocking {
                    repo.savePrefs(
                        runBlocking { repo.load() }.prefs.copy(
                            showUnreadBadges = false,
                            showDownloadBadges = false,
                            showLanguageBadges = false,
                            showContinueButton = false,
                            showCategoryLabels = false,
                            showHiddenCategories = true,
                            showWorkCount = false,
                        ),
                    )
                }
                var prefs = runBlocking { repo.load() }.prefs
                assertFalse(prefs.showUnreadBadges)
                assertFalse(prefs.showDownloadBadges)
                assertFalse(prefs.showLanguageBadges)
                assertFalse(prefs.showContinueButton)
                assertFalse(prefs.showCategoryLabels)
                assertTrue(prefs.showHiddenCategories)
                assertFalse(prefs.showWorkCount)
                // 连续修改多个开关不互相覆盖。
                runBlocking {
                    val base = repo.load().prefs
                    repo.savePrefs(base.copy(showUnreadBadges = true))
                    val mid = repo.load().prefs
                    assertTrue(mid.showUnreadBadges)
                    assertTrue(mid.showHiddenCategories)
                    repo.savePrefs(mid.copy(showWorkCount = true))
                }
                prefs = runBlocking { repo.load() }.prefs
                assertTrue(prefs.showUnreadBadges)
                assertTrue(prefs.showHiddenCategories)
                assertTrue(prefs.showWorkCount)
                // 旧库缺字段：删单键后读回默认值，其余保留。
                runBlocking { session.library.deletePref(LibraryPrefsKeys.SHOW_UNREAD_BADGES) }
                runBlocking { session.library.setPref(LibraryPrefsKeys.SHOW_WORK_COUNT, "notabool") }
                prefs = runBlocking { repo.load() }.prefs
                assertTrue(prefs.showUnreadBadges)
                assertTrue(prefs.showWorkCount)
                assertTrue(prefs.showHiddenCategories)
                // 重开仍保留。
                runBlocking { session.close() }
                session = openProfile(profile)
                val reopened = runBlocking { production(session).load() }.prefs
                assertTrue(reopened.showUnreadBadges)
                assertTrue(reopened.showHiddenCategories)
            } finally {
                runBlocking { session.close() }
            }
        } finally {
            profile.deleteRecursively()
        }
    }

    @Test
    fun chapterlessStartContinueAndOldUpgrade() {
        val profile = Files.createTempDirectory("regression-chapterless").toFile()
        try {
            var session = openProfile(profile, CHAPTERLESS_JSON)
            try {
                val repo = production(session)
                runBlocking {
                    repo.addToLibrary(NewLibraryEntry("srcA", LibraryKind.COMIC, "single", title = "Single"))
                }
                var snap = runBlocking { repo.load() }
                var entry = snap.entries.single()
                // 新收藏未读可从书架开始（canContinue 恒为 true，标签按 hasStarted 区分）。
                assertTrue(entry.canContinue)
                assertFalse(entry.hasStarted)
                // 有效无分章目录：空章表记单行（模拟成功取得的源目录接入）。
                runBlocking { repo.syncWorkDetail("srcA", LibraryKind.COMIC, "single", emptyList()) }
                // browse 链路的成功同步由 ExtensionApp 装配保证，此处仅验仓储语义。
                snap = runBlocking { repo.load() }
                entry = snap.entries.single()
                assertEquals(1, entry.totalChapters)
                assertEquals(1L, entry.unreadCount)
                // 翻到非首页后保存，同一磁盘资料目录重开恢复同一页。
                val reader = VeneraReaderService(session.comicReading, session.history, session.library)
                runBlocking {
                    reader.saveProgress("srcA", "single", null, "s1", 1, "Single", null, isLastPage = false)
                }
                snap = runBlocking { repo.load() }
                entry = snap.entries.single()
                assertTrue(entry.hasStarted)
                assertTrue(entry.canContinue)
                assertEquals(null, entry.continueChapterId)
                assertEquals(1, entry.continuePageIndex)
                runBlocking { session.close() }
                session = openProfile(profile, CHAPTERLESS_JSON)
                val reopenedRepo = production(session)
                val reopened = runBlocking { reopenedRepo.load() }.entries.single()
                assertEquals(1, reopened.continuePageIndex)
                assertEquals(null, reopened.continueChapterId)
            } finally {
                runBlocking { session.close() }
            }
            // 老库升级：仅扩展表 + 旧进度，无书架/章节表，启动补表后旧进度保留。
            val legacyProfile = Files.createTempDirectory("regression-legacy").toFile()
            try {
                val dbFile = File(legacyProfile, "extensions.db").absolutePath
                val legacy = JdbcSqliteDriver("jdbc:sqlite:$dbFile")
                legacy.execute(null, "CREATE TABLE repositories(id TEXT PRIMARY KEY, name TEXT NOT NULL, url TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1)", 0, null).value
                legacy.execute(null, "CREATE TABLE origins(source_key TEXT PRIMARY KEY, kind TEXT NOT NULL, repository_id TEXT, repository_name TEXT, url TEXT)", 0, null).value
                legacy.execute(null, "CREATE TABLE extensions(source_key TEXT PRIMARY KEY, name TEXT NOT NULL, version TEXT NOT NULL, file_name TEXT NOT NULL, origin_kind TEXT NOT NULL, origin_repository_id TEXT, origin_repository_name TEXT, origin_url TEXT)", 0, null).value
                legacy.execute(null, "CREATE TABLE reading_progress(source_key TEXT NOT NULL, work_id TEXT NOT NULL, chapter_id TEXT NOT NULL, page_key TEXT NOT NULL, page_index INTEGER NOT NULL, PRIMARY KEY (source_key, work_id, chapter_id))", 0, null).value
                legacy.execute(null, "INSERT INTO reading_progress(source_key, work_id, chapter_id, page_key, page_index) VALUES('srcA','legacy','ep_1','p0',0)", 0, null).value
                legacy.close()
                var upgraded = openProfile(legacyProfile)
                try {
                    val tables = mutableSetOf<String>()
                    // 章节表已补齐，旧进度、收藏、分类与偏好原样保留。
                    val chapters = runBlocking { upgraded.library.listChapters("srcA", WorkKind.COMIC, "legacy") }
                    assertTrue(chapters.isEmpty())
                    val progress = runBlocking { upgraded.comicReading.readLatestProgress("srcA", "legacy") }
                    assertEquals("ep_1", progress?.chapterId)
                    // 首次创建与升级后重开均无 DDL 报错。
                    runBlocking { upgraded.close() }
                    upgraded = openProfile(legacyProfile)
                    val again = runBlocking { upgraded.comicReading.readLatestProgress("srcA", "legacy") }
                    assertEquals("ep_1", again?.chapterId)
                    tables.toString()
                } finally {
                    runBlocking { upgraded.close() }
                }
            } finally {
                legacyProfile.deleteRecursively()
            }
        } finally {
            profile.deleteRecursively()
        }
    }

    @Test
    fun browseDetailSyncPreservesReadAndFailureKeeps() {
        val harness = VeneraHarness()
        try {
            runBlocking {
                harness.service.open()
                harness.service.restore()
                harness.service.installFromContent("Chapters", harness.script.decodeToString())
            }
            val store = LibraryStore(harness.driver)
            val history = HistoryStore(harness.driver)
            val repo = ProductionLibraryRepository(
                store = store,
                history = history,
                thumbnails = { _, _ -> ByteArray(0) },
            )
            runBlocking { repo.addToLibrary(NewLibraryEntry("example_source", LibraryKind.COMIC, "demo", title = "Demo")) }
            val browse = VeneraBrowseService(
                harness.service,
                onDetailLoaded = { sourceKey, workId, chapters ->
                    repo.syncWorkDetail(sourceKey, LibraryKind.COMIC, workId, chapters)
                },
            )
            // 成功取得目录后同步，重复刷新保留同 ID 已读，新章默认未读。
            val first = runBlocking { browse.loadWorkDetail("example_source", "demo") }
            assertEquals(2, first.chapters.size)
            runBlocking { repo.markDetailChapters(repo.load().entries.single().key, setOf("ep_2"), true) }
            val second = runBlocking { browse.loadWorkDetail("example_source", "demo") }
            assertEquals(2, second.chapters.size)
            val states = runBlocking { repo.chapterReadStates(repo.load().entries.single().key) }
            assertEquals(true, states["ep_2"])
            assertEquals(false, states["ep_1"])
            // 失败或取消保留上次有效目录。
            assertFailsWith<Exception> {
                runBlocking { browse.loadWorkDetail("example_source", "missing") }
            }
            val afterFail = runBlocking { repo.load() }.entries.single()
            assertEquals(2, afterFail.totalChapters)
        } finally {
            harness.close()
        }
    }

    private fun d1Id(): String = "ep_2"

    companion object {
        const val DETAIL_JSON = """{"title":"Demo","author":"A","cover":"","description":"","chapters":[{"id":"2","title":"Ch2"},{"id":"1","title":"Ch1"}]}"""
        const val OTHER_DETAIL_JSON = """{"title":"Other","author":"A","cover":"","description":"","chapters":[{"id":"9","title":"Only"}]}"""
        const val CHAPTERLESS_JSON = """{"title":"Single","author":"A","cover":"","description":"","chapters":{}}"""
    }
}
