package app.koyura.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import app.koyura.app.reader.VeneraReaderService
import app.koyura.app.browse.venera.VeneraBrowseService
import app.koyura.app.history.VeneraHistoryService
import app.koyura.app.library.ProductionLibraryRepository
import app.koyura.app.account.ProductionAccountRepository
import app.koyura.app.updates.ProductionUpdatesRepository
import app.koyura.app.updates.ProductionUpdatePolicyRepository
import app.koyura.app.resources.Res
import app.koyura.app.resources.extension_startup_failed
import app.koyura.app.resources.extension_startup_loading
import app.koyura.app.resources.extension_startup_retry
import app.koyura.app.sourceui.FakeClipboard
import app.koyura.app.sourceui.SourceUiHost
import app.koyura.data.session.ExtensionSession
import app.koyura.designsystem.ReaderTheme
import app.koyura.source.runtime.VeneraEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import androidx.compose.runtime.DisposableEffect
import org.jetbrains.compose.resources.stringResource

@Composable
internal expect fun PlatformExtensionApp()

@Composable
internal fun extensionEnvironment(platform: String): VeneraEnvironment {
    val locale = LocalLocale.current
    val veneraLocale = if (locale.region.isBlank()) locale.language else "${locale.language}_${locale.region}"
    return VeneraEnvironment(
        compatibleAppVersion = "1.16.0",
        locale = veneraLocale,
        platform = platform,
    )
}

@Composable
internal fun ExtensionApp(
    uiHost: SourceUiHost = SourceUiHost(FakeClipboard()),
    openSession: suspend (SourceUiHost) -> ExtensionSession,
) {
    var openedSession by remember { mutableStateOf<ExtensionSession?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        var session: ExtensionSession? = null
        try {
            // Capture ownership on the opening dispatcher before a cancelled caller can lose it.
            withContext(Dispatchers.IO) { session = openSession(uiHost) }
            openedSession = checkNotNull(session)
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                session?.close()
            }
        }
    }

    val session = openedSession
    if (session != null) {
        // 更新扫描出新章后的自动下载钩子（偏好默认关闭，开启后新章自动入队）。
        session.updates.setNewChaptersDownloadHook(session.downloadTriggers)
        // 后台更新调度：进程内时间驱动 + 启动补跑；作用域随会话销毁，前台唤醒由平台接线补充。
        val sessionScope = remember(session) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        LaunchedEffect(session) { session.updateScheduler.start(sessionScope) }
        DisposableEffect(session) {
            onDispose { sessionScope.cancel() }
        }
        val library = remember(session) {
            ProductionLibraryRepository(
                store = session.library,
                history = session.history,
                thumbnails = { sourceKey, imageKey ->
                    session.comicReading.loadThumbnailBytes(sourceKey, imageKey)
                },
                downloadedCounts = { sourceKey, workId ->
                    session.downloads.countCompleted(sourceKey, workId).toLong()
                },
            )
        }
        val updates = remember(session) {
            ProductionUpdatesRepository(session.updates)
        }
        val updatePolicy = remember(session) {
            ProductionUpdatePolicyRepository(session.updates)
        }
        val accounts = remember(session) {
            ProductionAccountRepository(session.service)
        }
        val browse = remember(session) {
            VeneraBrowseService(
                extensions = session.service,
                thumbnails = { sourceKey, imageKey ->
                    session.comicReading.loadThumbnailBytes(sourceKey, imageKey)
                },
                onDetailLoaded = { sourceKey, workId, chapters ->
                    library.syncWorkDetail(
                        sourceKey = sourceKey,
                        kind = app.koyura.features.library.LibraryKind.COMIC,
                        workId = workId,
                        chapters = chapters,
                    )
                },
                search = session.service.browse,
            )
        }
        val reader = remember(session) {
            VeneraReaderService(
                reading = session.comicReading,
                history = session.history,
                library = session.library,
            )
        }
        val history = remember(session) { VeneraHistoryService(session.history, session.library) }
        App(
            browseService = browse,
            readerService = reader,
            historyService = history,
            readerPreferences = session.readerPreferences,
            libraryRepository = library,
            updatesRepository = updates,
            updatePolicyRepository = updatePolicy,
            accountRepository = accounts,
            sourceUiHost = uiHost,
        )
    } else {
        ReaderTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    if (failed) {
                        Text(stringResource(Res.string.extension_startup_failed))
                        Button(onClick = {
                            failed = false
                            attempt += 1
                        }) { Text(stringResource(Res.string.extension_startup_retry)) }
                    } else {
                        CircularProgressIndicator()
                        Text(stringResource(Res.string.extension_startup_loading))
                    }
                }
            }
        }
    }
}
