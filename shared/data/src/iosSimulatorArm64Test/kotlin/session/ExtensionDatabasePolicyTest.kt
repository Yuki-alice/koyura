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
                val driver = createExtensionDriver(ExtensionDatabaseName, root.toString())
                try {
                    assertExtensionDatabasePolicy(driver)
                } finally {
                    driver.close()
                }
            }
        } finally {
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }
}
