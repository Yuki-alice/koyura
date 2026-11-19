package app.koyura.source.runtime

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceUiTest {
    @Test
    fun dispatchesDialogsValidationClipboardAndSourceOwnedLoading() = runTest {
        val host = RecordingUi()
        val runtime = VeneraSourceRuntime.create(
            VeneraEnvironment("1.16.0", "zh_CN", "test"), SourceSessions(), host,
        ) { error("Unexpected network request") }
        try {
            runtime.register(script)
            val value = runtime.runSetting("interactive", "test")
            assertEquals(JsonArray(listOf(JsonPrimitive("source clipboard"), JsonPrimitive("expected"), JsonPrimitive(1))), value)
            assertEquals(listOf("Selected", "Ready"), host.messages)
            assertEquals(listOf("created", "closed"), host.loading)
            assertEquals(listOf("warning:JS Console:probe"), host.logs)
            assertEquals("https://example.test/", host.url)
            runtime.unregister("interactive")
            assertEquals(listOf("created", "closed"), host.loading)
        } finally {
            runtime.close()
        }
    }

    private class RecordingUi : VeneraUiHost {
        val messages = mutableListOf<String>()
        val loading = mutableListOf<String>()
        val logs = mutableListOf<String>()
        var url: String? = null
        private var clipboard: String? = null

        override fun showMessage(sourceKey: String, message: String) {
            assertEquals("interactive", sourceKey)
            messages += message
        }

        override fun log(sourceKey: String?, level: String, title: String, content: String) {
            assertEquals("interactive", sourceKey)
            logs += "$level:$title:$content"
        }

        override fun launchUrl(sourceKey: String, url: String) {
            this.url = url
        }

        override fun showLoading(sourceKey: String, id: Int, onCancel: (() -> Unit)?) {
            assertTrue(id > 0)
            assertNull(onCancel)
            loading += "created"
        }

        override fun cancelLoading(sourceKey: String, id: Int) {
            loading += "closed"
        }

        override suspend fun showDialog(
            sourceKey: String, title: String, content: String, actions: List<SourceDialogAction>, runAction: suspend (Int) -> Unit,
        ) {
            assertEquals(listOf(SourceDialogAction("Confirm", "filled")), actions)
            runAction(0)
            runAction(0)
        }

        override suspend fun showInputDialog(
            sourceKey: String, title: String, image: SourceDialogImage?, validate: (suspend (String) -> String?)?,
        ): String {
            assertTrue(image is SourceDialogImage.Bytes && image.value.contentEquals(byteArrayOf(1, 2)))
            assertEquals("Try again", validate!!("invalid"))
            assertNull(validate("expected"))
            return "expected"
        }

        override suspend fun showSelectDialog(sourceKey: String, title: String, options: List<String>, initialIndex: Int?): Int {
            assertEquals(0, initialIndex)
            assertEquals(listOf("A", "B"), options)
            return 1
        }

        override suspend fun setClipboard(sourceKey: String, text: String) {
            clipboard = text
        }

        override suspend fun getClipboard(sourceKey: String): String? = clipboard
    }

    private val script = """
        class Source extends ComicSource {
            name = "Interactive"; key = "interactive"; version = "1.0.0"; minAppVersion = "1.4.0";
            settings = { test: { type: "callback", callback: async () => {
                console.warn("probe");
                UI.launchUrl("https://example.test/");
                const loading = UI.showLoading();
                await setClipboard("source clipboard");
                const read = await getClipboard();
                const input = await UI.showInputDialog("Input", input => input === "expected" ? null : "Try again",
                    new Uint8Array([1, 2]).buffer);
                const selection = await UI.showSelectDialog("Choose", ["A", "B"], 0);
                await new Promise(resolve => {
                    const returned = UI.showDialog("Title", "Confirm", [{
                        text: "Confirm", style: "filled", callback: async () => {
                            UI.showMessage("Selected");
                            resolve();
                        }
                    }]);
                    if (returned !== undefined) throw "showDialog should not return a promise";
                });
                UI.cancelLoading(loading);
                UI.showMessage("Ready");
                return [read, input, selection];
            }}};
        }
    """.trimIndent()
}
