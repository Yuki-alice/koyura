package app.koyura.app.updates

import app.koyura.data.session.openExtensionSession
import app.koyura.domain.library.WorkKind
import app.koyura.domain.updates.SmartUpdatePreferences
import app.koyura.domain.updates.UpdateFailureWorkKey
import app.koyura.source.runtime.VeneraEnvironment
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ProductionUpdateFailuresTest {
    @Test
    fun groupsFailuresBySourceAndWorkAndPersistsBulkRemoval() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "update-failures-${Uuid.random()}"
        val environment = VeneraEnvironment("1.16.0", "en", "test")
        val first = UpdateFailureWorkKey("first", WorkKind.COMIC, "work")
        val second = UpdateFailureWorkKey("second", WorkKind.COMIC, "work")
        val kept = UpdateFailureWorkKey("second", WorkKind.COMIC, "keep")
        try {
            val original = openExtensionSession(root, environment)
            try {
                for (key in listOf(first, second, kept)) {
                    original.library.addToLibrary(key.sourceKey, key.kind, key.workId, key.workId, addedAtMillis = 1)
                }
                original.updates.setPreferences(SmartUpdatePreferences(enabled = false))
                val repository = ProductionUpdatesRepository(original.updates)
                repository.checkNow()
                val groups = repository.failuresByWork()
                assertEquals(setOf(first, second, kept), groups.keys)
                assertTrue(groups.values.all { it.size == 1 })
                repository.clearFailures(setOf(first, second))
                repository.clearFailures(setOf(first))
                assertEquals(listOf("keep"), repository.failures().map { it.workId })
            } finally {
                original.close()
            }
            val reopened = openExtensionSession(root, environment)
            try {
                assertEquals(setOf(kept), ProductionUpdatesRepository(reopened.updates).failuresByWork().keys)
            } finally {
                reopened.close()
            }
        } finally {
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }
}
