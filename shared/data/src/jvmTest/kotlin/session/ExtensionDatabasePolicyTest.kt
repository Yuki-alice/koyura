package app.koyura.data.session

import okio.FileSystem
import kotlin.test.Test
import kotlin.uuid.Uuid

class ExtensionDatabasePolicyTest {
    @Test
    fun fileBackedDriverUsesWalAndBusyTimeoutAcrossReopen() {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "sqlite-policy-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(root)
        try {
            repeat(2) {
                createExtensionDriver(root / ExtensionDatabaseName).use(::assertExtensionDatabasePolicy)
            }
        } finally {
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }
}
