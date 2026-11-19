package app.koyura.data.reader.preferences

import app.koyura.domain.reader.preferences.ReaderFit
import app.koyura.domain.reader.preferences.ReaderLayout
import app.koyura.domain.reader.preferences.ReaderSettings
import app.koyura.domain.reader.preferences.ReadingMode
import app.koyura.domain.reader.preferences.OverrideField
import app.koyura.domain.reader.preferences.WorkReaderOverride
import app.koyura.domain.reader.preferences.WorkReaderOverridePatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Sink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ReaderPreferencesStoreTest {
    private fun store(dir: String, hooks: PreferenceUpdateHooks = PreferenceUpdateHooks()) =
        ReaderPreferencesStore(
            directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve(dir),
            fileSystem = FileSystem.SYSTEM,
            hooks = hooks,
        )

    @Test
    fun freshStoreReturnsDefaults() = runTest {
        val dir = "chrome-prefs-${randomSuffix()}"
        val store = store(dir)
        assertEquals(ReaderSettings(), store.readGlobal())
        assertNull(store.readOverride("example_source", "demo"))
        assertEquals(ReaderSettings(), store.resolve("example_source", "demo"))
    }

    @Test
    fun backgroundPreferenceRoundTripsAndOldConfigsKeepAutomatic() = runTest {
        val dir = "chrome-prefs-${randomSuffix()}"
        val store = store(dir)
        store.writeGlobal(ReaderSettings(background = app.koyura.domain.reader.preferences.ReaderBackground.BLACK))
        val reopened = store(dir)
        assertEquals(
            app.koyura.domain.reader.preferences.ReaderBackground.BLACK,
            reopened.readGlobal().background,
        )
        // 旧配置文件没有 background 字段（老版本写入），解码后保持 Automatic 默认。
        val legacyDir = "chrome-prefs-${randomSuffix()}"
        val legacyDirectory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve(legacyDir)
        FileSystem.SYSTEM.createDirectories(legacyDirectory)
        FileSystem.SYSTEM.write(legacyDirectory / "reader_global.json") {
            writeUtf8("""{"mode":"WEBTOON","layout":"AUTOMATIC","fit":"FIT"}""")
        }
        val legacy = store(legacyDir)
        assertEquals(
            app.koyura.domain.reader.preferences.ReaderBackground.AUTOMATIC,
            legacy.readGlobal().background,
        )
        assertEquals(ReadingMode.WEBTOON, legacy.readGlobal().mode)
    }

    @Test
    fun newInstanceOnSameDirectorySeesWrites() = runTest {
        val dir = "chrome-prefs-${randomSuffix()}"
        store(dir).writeGlobal(ReaderSettings(mode = ReadingMode.RIGHT_TO_LEFT))
        val reopened = store(dir)
        assertEquals(ReadingMode.RIGHT_TO_LEFT, reopened.readGlobal().mode)
        assertEquals(ReadingMode.RIGHT_TO_LEFT, reopened.resolve("example_source", "demo").mode)
    }

    @Test
    fun singleWorkOverrideMergesOverGlobal() = runTest {
        val dir = "chrome-prefs-${randomSuffix()}"
        val store = store(dir)
        store.writeOverride("example_source", "demo", WorkReaderOverridePatch(mode = OverrideField.Set(ReadingMode.WEBTOON)))
        store.writeOverride("example_source", "demo", WorkReaderOverridePatch(cropBorders = OverrideField.Set(true)))
        val effective = store.resolve("example_source", "demo")
        assertEquals(ReadingMode.WEBTOON, effective.mode)
        assertEquals(true, effective.cropBorders)
        assertEquals(ReaderLayout.AUTOMATIC, effective.layout)
        // Untouched work inherits the global value.
        assertEquals(ReadingMode.LEFT_TO_RIGHT, store.resolve("example_source", "other").mode)
    }

    @Test
    fun sameTitleWorksUnderTwoSourcesAreIsolated() = runTest {
        val dir = "chrome-prefs-${randomSuffix()}"
        val store = store(dir)
        store.writeOverride("source_a", "demo", WorkReaderOverridePatch(fit = OverrideField.Set(ReaderFit.FIT_WIDTH)))
        store.writeOverride("source_b", "demo", WorkReaderOverridePatch(fit = OverrideField.Set(ReaderFit.ORIGINAL)))
        assertEquals(ReaderFit.FIT_WIDTH, store.resolve("source_a", "demo").fit)
        assertEquals(ReaderFit.ORIGINAL, store.resolve("source_b", "demo").fit)
    }

    @Test
    fun clearingOverrideRestoresGlobal() = runTest {
        val dir = "chrome-prefs-${randomSuffix()}"
        val store = store(dir)
        store.writeOverride("example_source", "demo", WorkReaderOverridePatch(mode = OverrideField.Set(ReadingMode.VERTICAL)))
        assertEquals(ReadingMode.VERTICAL, store.resolve("example_source", "demo").mode)
        store.clearOverride("example_source", "demo")
        assertNull(store.readOverride("example_source", "demo"))
        assertEquals(ReaderSettings(), store.resolve("example_source", "demo"))
    }

    @Test
    fun legacyConfigWithMissingFieldsFallsBackToDefaults() = runTest {
        val dir = "chrome-prefs-${randomSuffix()}"
        val fs = FileSystem.SYSTEM
        val base = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve(dir)
        fs.createDirectories(base)
        // Old config predating fit/cropBorders/zoomOnDoubleTap, with an unknown key.
        fs.write(base / "reader_global.json") {
            writeUtf8("""{"mode":"WEBTOON","layout":"SINGLE_PAGE","unknownKey":1}""")
        }
        val store = ReaderPreferencesStore(directory = base, fileSystem = fs)
        val global = store.readGlobal()
        assertEquals(ReadingMode.WEBTOON, global.mode)
        assertEquals(ReaderLayout.SINGLE_PAGE, global.layout)
        assertEquals(ReaderFit.FIT, global.fit)
        assertEquals(false, global.cropBorders)
        assertEquals(true, global.zoomOnDoubleTap)
    }

    private fun randomSuffix(): String = (0..Int.MAX_VALUE).random().toString(36)

    /**
     * 复现 4/5：先保存 WEBTOON 与 FIT_WIDTH 覆盖，再清除 mode 字段。
     * 期望 mode 覆盖为空、有效模式继承全局、FIT_WIDTH 保留。
     */
    @Test
    fun clearingSingleFieldKeepsOtherFields() = runTest {
        val dir = "chrome-prefs-${randomSuffix()}"
        val store = store(dir)
        store.writeOverride(
            "example_source",
            "demo",
            WorkReaderOverridePatch(mode = OverrideField.Set(ReadingMode.WEBTOON)),
        )
        store.writeOverride(
            "example_source",
            "demo",
            WorkReaderOverridePatch(fit = OverrideField.Set(ReaderFit.FIT_WIDTH)),
        )
        store.writeOverride(
            "example_source",
            "demo",
            WorkReaderOverridePatch(mode = OverrideField.Clear),
        )
        // 同目录新实例读取。
        val reopened = store(dir)
        assertEquals(null, reopened.readOverride("example_source", "demo")?.mode)
        assertEquals(ReadingMode.LEFT_TO_RIGHT, reopened.resolve("example_source", "demo").mode)
        assertEquals(ReaderFit.FIT_WIDTH, reopened.resolve("example_source", "demo").fit)
    }

    /**
     * Gate hooks: the first update pauses after reading, so the second update is guaranteed to
     * be queued on the mutex while the first one is still open.
     */
    private class GateHooks : PreferenceUpdateHooks() {
        private val lock = Mutex()
        private var calls = 0
        val firstRead = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun beforeUpdate(key: String) {
            val second = lock.withLock {
                ++calls
                calls == 2
            }
            if (second) secondStarted.complete(Unit)
        }

        override suspend fun afterRead(key: String) {
            val first = lock.withLock { calls <= 1 }
            if (first) {
                firstRead.complete(Unit)
                release.await()
            }
        }
    }

    /**
     * 复现 5/5：同一目录、同一作品并发写两个补丁（mode=WEBTOON 与 fit=FIT_WIDTH），第一笔
     * 在读旧值后暂停，让第二笔重叠。不要求提交顺序，只断言重开后两字段都保留。
     */
    @Test
    fun overlappingPatchesMergeWithoutLoss() = runTest {
        val dir = "chrome-prefs-${randomSuffix()}"
        val hooks = GateHooks()
        val store = store(dir, hooks)
        withContext(Dispatchers.Default) {
            val first = async {
                store.writeOverride(
                    "example_source",
                    "demo",
                    WorkReaderOverridePatch(mode = OverrideField.Set(ReadingMode.WEBTOON)),
                )
            }
            hooks.firstRead.await()
            val second = async {
                store.writeOverride(
                    "example_source",
                    "demo",
                    WorkReaderOverridePatch(fit = OverrideField.Set(ReaderFit.FIT_WIDTH)),
                )
            }
            hooks.secondStarted.await()
            hooks.release.complete(Unit)
            first.await()
            second.await()
        }
        val reopened = store(dir)
        assertEquals(ReadingMode.WEBTOON, reopened.resolve("example_source", "demo").mode)
        assertEquals(ReaderFit.FIT_WIDTH, reopened.resolve("example_source", "demo").fit)
    }

    /** 写入与清除交错：写操作先持有更新锁，清除后排队，最终一致为空且全局不受影响。 */
    @Test
    fun overlappingWriteAndClearStayConsistent() = runTest {
        val dir = "chrome-prefs-${randomSuffix()}"
        val hooks = GateHooks()
        val store = store(dir, hooks)
        withContext(Dispatchers.Default) {
            val write = async {
                store.writeOverride(
                    "example_source",
                    "demo",
                    WorkReaderOverridePatch(mode = OverrideField.Set(ReadingMode.WEBTOON)),
                )
            }
            hooks.firstRead.await()
            val clear = async { store.clearOverride("example_source", "demo") }
            hooks.secondStarted.await()
            hooks.release.complete(Unit)
            write.await()
            clear.await()
        }
        val reopened = store(dir)
        assertNull(reopened.readOverride("example_source", "demo"))
        assertEquals(ReaderSettings(), reopened.resolve("example_source", "demo"))
    }

    private class FailingWriteFs :
        ForwardingFileSystem(FileSystem.SYSTEM) {
        override fun sink(file: Path, mustCreate: Boolean): Sink =
            throw IOException("read-only profile")
    }

    /** 写失败时原配置仍可读：临时文件写一半也不会污染已提交的配置。 */
    @Test
    fun failedWriteKeepsPreviousConfig() = runTest {
        val dir = "chrome-prefs-${randomSuffix()}"
        val base = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve(dir)
        val good = ReaderPreferencesStore(directory = base, fileSystem = FileSystem.SYSTEM)
        good.writeGlobal(ReaderSettings(mode = ReadingMode.RIGHT_TO_LEFT))
        good.writeOverride(
            "example_source",
            "demo",
            WorkReaderOverridePatch(mode = OverrideField.Set(ReadingMode.WEBTOON)),
        )
        val bad = ReaderPreferencesStore(directory = base, fileSystem = FailingWriteFs())
        assertFailsWith<IOException> { bad.writeGlobal(ReaderSettings()) }
        assertFailsWith<IOException> {
            bad.writeOverride(
                "example_source",
                "demo",
                WorkReaderOverridePatch(fit = OverrideField.Set(ReaderFit.FIT_WIDTH)),
            )
        }
        val reread = ReaderPreferencesStore(directory = base, fileSystem = FileSystem.SYSTEM)
        assertEquals(ReadingMode.RIGHT_TO_LEFT, reread.readGlobal().mode)
        assertEquals(ReadingMode.WEBTOON, reread.resolve("example_source", "demo").mode)
    }
}
