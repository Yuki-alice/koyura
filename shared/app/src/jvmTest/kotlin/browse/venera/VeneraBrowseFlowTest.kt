package app.koyura.app.browse.venera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.koyura.app.App
import app.koyura.app.AppScaffold
import app.koyura.app.NavigationBarTestTag
import app.koyura.app.browse.BrowseExtensionQueryTag
import app.koyura.app.browse.BrowseExtensionsListTag
import app.koyura.app.browse.BrowseExtensionsTabTag
import app.koyura.app.browse.BrowseManageStoresTag
import app.koyura.app.browse.BrowseRootTestTag
import app.koyura.app.browse.BrowseService
import app.koyura.app.browse.BrowseSession
import app.koyura.app.browse.BrowseSourcesTabTag
import app.koyura.app.browse.CatalogDialogTag
import app.koyura.app.browse.CatalogState
import app.koyura.app.browse.CatalogVariantId
import app.koyura.app.browse.OperationStatus
import app.koyura.app.browse.SourceSearchQueryTag
import app.koyura.app.browse.SourceSearchResultsTag
import app.koyura.app.browse.SourceSearchSubmitTag
import app.koyura.app.browse.StoresScreenTestTag
import app.koyura.app.browse.extensionRowTag
import app.koyura.app.browse.variantRefs
import app.koyura.app.browse.sourceResultTag
import app.koyura.app.browse.storePreviewTag
import app.koyura.app.browse.storeRowTag
import app.koyura.app.navigation.createAppNavigationState
import app.koyura.data.extension.ExtensionDb
import app.koyura.data.extension.ExtensionService
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.navigation.NavTab
import app.koyura.network.applyReaderDefaults
import app.koyura.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * End-to-end coverage with the production stack: real SQLDelight, real script files, real QuickJS
 * and Ktor MockEngine responses.
 *
 * The upstream `minimal_source.js` is installed verbatim: the suite asserts the SHA-256 of the
 * persisted script matches the upstream hash, then drives search (`search.load`) and detail
 * (`comic.loadInfo`) through the real shell.
 */
class VeneraBrowseFlowTest {
    @Test
    fun detailAcceptsUngroupedWorksAndGroupedChapters() = runBlocking {
        val harness = VeneraHarness(
            """
            class ChapterSource extends ComicSource {
                name = "Chapters";
                key = "chapter_source";
                version = "1.0.0";
                minAppVersion = "1.16.0";
                comic = {
                    loadInfo(id) {
                        return {title: id, chapters: id === "single" ? null : {
                            Main: {ep_2: "Chapter Two", ep_1: "Chapter One"},
                            Extras: {ep_extra: "Extra"}
                        }};
                    }
                };
            }
            """.trimIndent().encodeToByteArray(),
        )
        try {
            harness.service.open()
            harness.service.restore()
            harness.service.installFromContent("Chapters", harness.script.decodeToString())
            val browse = harness.browse()
            assertTrue(browse.loadWorkDetail("chapter_source", "single").chapters.isEmpty())
            val grouped = browse.loadWorkDetail("chapter_source", "grouped")
            assertEquals(listOf("ep_2", "ep_1", "ep_extra"), grouped.chapters.map { it.id })
        } finally {
            harness.close()
        }
    }

    @Test
    fun installUpstreamScriptVerifiesShaAndRegistersSource() {
        val harness = realHarness()
        try {
            runBlocking {
                harness.service.open()
                harness.service.restore()
            }
            val browse = harness.browse()
            val session = BrowseSession(browse)
            runBlocking {
                session.refresh()
                assertTrue(session.addStore("Example", VeneraFixtures.IndexUrl))
                val storeId = session.state.stores.single().id
                session.previewStore(storeId)
                val ready = session.state.catalogStates[storeId] as CatalogState.Ready
                val variant = ready.catalog.variantRefs().single().variant
                assertEquals(storeId, variant.repositoryId)
                assertEquals("example_source", variant.sourceKey)
                assertEquals(VeneraFixtures.ScriptUrl, variant.scriptUrl)
                assertEquals(VeneraFixtures.IndexUrl, variant.repositoryIndexUrl)
                assertTrue(session.installVariant(variant))
            }

            assertEquals(VeneraFixtures.ExpectedScriptSha256, harness.installedScriptSha())
            runBlocking {
                val live = harness.service.runtime!!.registeredSources()
                assertEquals(listOf("example_source"), live.map { it.key })
                assertEquals("1.0.0", live.single().version)
            }
            runBlocking { session.refresh() }
            val installed = session.state.extensions.single()
            assertEquals("example_source", installed.packageName)
            assertEquals("1.0.0", installed.versionName)
            assertTrue(session.state.sources.any { it.id == "example_source" && it.name == "Example Source" })
        } finally {
            harness.close()
        }
    }

    @Test
    fun browseToSearchToDetailThroughRealShell() = runComposeUiTest {
        val harness = realHarness()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        val session = BrowseSession(harness.browse())
        try {
            runBlocking {
                harness.service.open()
                harness.service.restore()
                session.refresh()
                assertTrue(session.addStore("Example", VeneraFixtures.IndexUrl))
            }
            val storeId = session.state.stores.single().id
            setContent {
                ReaderTheme {
                    AppScaffold(navigation, useRail = false, browseService = null, browseSession = session)
                }
            }

            // Install from the store preview; the row lands in the installed group.
            onNodeWithTag(BrowseExtensionsTabTag).performClick()
            onNodeWithTag(BrowseManageStoresTag).performClick()
            onNodeWithTag(StoresScreenTestTag).assertIsDisplayed()
            onNodeWithTag(storePreviewTag(storeId)).performClick()
            onNodeWithTag(CatalogDialogTag).assertIsDisplayed()
            val installTag = "catalog-install-${CatalogVariantId(storeId, "example_source", VeneraFixtures.ScriptUrl).key}"
            onNodeWithTag(installTag).performClick()
            onNodeWithTag("catalog-close").performClick()
            onNodeWithTag("stores-back").performClick()
            onNodeWithTag(extensionRowTag("${storeId}::example_source")).assertIsDisplayed()
            assertEquals(VeneraFixtures.ExpectedScriptSha256, harness.installedScriptSha())

            // Sources pane shows the installed source; open its search page.
            onNodeWithTag(BrowseSourcesTabTag).performClick()
            onNodeWithTag("source-row-example_source").performClick()

            // search.load runs through QuickJS and its results render.
            onNodeWithTag(SourceSearchQueryTag).performTextInput("星")
            onNodeWithTag(SourceSearchSubmitTag).performClick()
            awaitVisible(sourceResultTag("demo"))
            onNodeWithTag(sourceResultTag("demo")).performClick()

            // comic.loadInfo renders the real detail.
            onNodeWithTag("source-detail-heading").assertIsDisplayed()
            onNodeWithText("作者").assertIsDisplayed()
            onNodeWithTag("source-detail-chapter-ep_2").assertIsDisplayed()

            // Back returns to the retained search results.
            navigation.handleBack()
            onNodeWithTag(SourceSearchResultsTag).assertIsDisplayed()
            onNodeWithTag(sourceResultTag("demo")).assertIsDisplayed()

            // Cross-tab and back: the detail page is still on the Browse stack.
            navigation.select(NavTab.Updates)
            navigation.select(NavTab.Browse)
            onNodeWithTag(SourceSearchResultsTag).assertIsDisplayed()
        } finally {
            harness.close()
        }
    }

    @Test
    fun failedPreviewRequestRetriesInDialog() = runComposeUiTest {
        val harness = realHarness()
        harness.indexFailures = 1
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        val session = BrowseSession(harness.browse())
        try {
            runBlocking {
                harness.service.open()
                harness.service.restore()
                session.refresh()
                assertTrue(session.addStore("Example", VeneraFixtures.IndexUrl))
            }
            val storeId = session.state.stores.single().id
            setContent {
                ReaderTheme {
                    AppScaffold(navigation, useRail = true, browseService = null, browseSession = session)
                }
            }

            onNodeWithTag(BrowseManageStoresTag).performClick()
            onNodeWithTag(storePreviewTag(storeId)).performClick()

            // First attempt hits the failing index; the dialog offers a retry.
            onNodeWithTag(CatalogDialogTag).assertIsDisplayed()
            onNodeWithTag("catalog-retry").assertIsDisplayed()
            onNodeWithTag("catalog-retry").performClick()

            // Retry succeeds and the install goes through.
            val installTag = "catalog-install-${CatalogVariantId(storeId, "example_source", VeneraFixtures.ScriptUrl).key}"
            onNodeWithTag(installTag).assertIsDisplayed()
            onNodeWithTag(installTag).performClick()
            onNodeWithTag("catalog-close").performClick()
            onNodeWithTag("stores-back").performClick()
            onNodeWithTag(BrowseExtensionsTabTag).performClick()
            onNodeWithTag(extensionRowTag("${storeId}::example_source")).assertIsDisplayed()
            assertEquals(VeneraFixtures.ExpectedScriptSha256, harness.installedScriptSha())
        } finally {
            harness.close()
        }
    }

    @Test
    fun staleInstallAfterUrlChangeFailsAndFreshPreviewSucceeds() {
        val harness = realHarness()
        val browse = harness.browse()
        val session = BrowseSession(browse)
        try {
            runBlocking {
                harness.service.open()
                harness.service.restore()
                session.refresh()
                assertTrue(session.addStore("Example", VeneraFixtures.IndexUrl))
            }
            val storeId = session.state.stores.single().id
            val stale = runBlocking {
                session.previewStore(storeId)
                (session.state.catalogStates[storeId] as CatalogState.Ready)
                    .catalog.variantRefs().single().variant
            }

            runBlocking {
                assertTrue(session.saveStore(storeId, "Example", "https://moved.example/index.json"))
            }
            assertEquals(null, session.state.catalogStates[storeId])

            // Installing the pre-edit variant fails against the moved index...
            runBlocking {
                assertEquals(false, session.installVariant(stale))
                val failed = session.state.itemOperations[stale.key]
                assertTrue(failed is OperationStatus.Failed, "stale variant must fail, got $failed")
            }

            // ...and a fresh preview of the new index installs cleanly.
            runBlocking {
                session.previewStore(storeId)
                val fresh = (session.state.catalogStates[storeId] as CatalogState.Ready)
                    .catalog.variantRefs().single().variant
                assertEquals("https://moved.example/index.json", fresh.repositoryIndexUrl)
                assertTrue(session.installVariant(fresh))
            }
            assertEquals(VeneraFixtures.ExpectedScriptSha256, harness.installedScriptSha())
        } finally {
            harness.close()
        }
    }

    @Test
    fun normalEntryPointReachesBrowseWithRealService() = runComposeUiTest {
        val harness = realHarness()
        try {
            runBlocking {
                harness.service.open()
                harness.service.restore()
            }
            setContent { App(browseService = harness.browse()) }

            // Tabs render in canonical order (Library, Updates, History, Browse, More).
            val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
            onAllNodes(tabs)[3].performClick()
            onNodeWithTag(BrowseRootTestTag).assertIsDisplayed()
        } finally {
            harness.close()
        }
    }

    @Test
    fun narrowAndWideLayoutsKeepBrowseState() = runComposeUiTest {
        val harness = realHarness()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        val session = BrowseSession(harness.browse())
        var width = mutableStateOf<androidx.compose.ui.unit.Dp>(719.dp)
        try {
            runBlocking {
                harness.service.open()
                harness.service.restore()
                session.refresh()
                assertTrue(session.addStore("Example", VeneraFixtures.IndexUrl))
            }
            val storeId = session.state.stores.single().id
            runBlocking {
                session.previewStore(storeId)
                session.installVariant(
                    (session.state.catalogStates[storeId] as CatalogState.Ready)
                        .catalog.variantRefs().single().variant,
                )
            }
            setContent {
                ReaderTheme {
                    Box(Modifier.size(width.value, 700.dp)) {
                        AppScaffold(
                            navigation = navigation,
                            useRail = width.value >= 720.dp,
                            browseService = null,
                            browseSession = session,
                        )
                    }
                }
            }

            onNodeWithTag(NavigationBarTestTag).assertIsDisplayed()
            onNodeWithTag(BrowseSourcesTabTag).performClick()
            onNodeWithTag("source-row-example_source").performClick()
            onNodeWithTag(SourceSearchQueryTag).performTextInput("星")
            onNodeWithTag(SourceSearchSubmitTag).performClick()
            awaitVisible(sourceResultTag("demo"))

            runOnIdle { width.value = 720.dp }
            waitForIdle()
            onNodeWithTag(SourceSearchQueryTag).assertIsDisplayed()
            // The row must survive the width switch; the list may need a moment to settle its
            // layout after the bar/rail swap.
            awaitVisible(sourceResultTag("demo"))
            assertEquals(NavTab.Browse, navigation.selectedTab)
        } finally {
            harness.close()
        }
    }

    private fun realHarness(): VeneraHarness =
        VeneraHarness(VeneraFixtures.upstreamScriptBytes())

    /**
     * Polls for visibility instead of asserting a single frame: search results arrive from real
     * QuickJS work on background threads, and the list also re-settles after a width switch.
     */
    private fun androidx.compose.ui.test.ComposeUiTest.awaitVisible(testTag: String) {
        val deadline = System.currentTimeMillis() + 10000
        var shown = false
        var lastError: AssertionError? = null
        while (!shown && System.currentTimeMillis() < deadline) {
            try {
                onNodeWithTag(testTag).assertIsDisplayed()
                shown = true
            } catch (failure: AssertionError) {
                lastError = failure
                waitForIdle()
                Thread.sleep(100)
            }
        }
        if (!shown) throw lastError ?: AssertionError("node $testTag never became visible")
    }
}
