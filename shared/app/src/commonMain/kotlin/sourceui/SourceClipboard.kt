package app.koyura.app.sourceui

/**
 * 平台剪贴板（脚本 `getClipboard`/`setClipboard` 的宿主侧实现）。
 *
 * 普通接口 + 平台各自实现，不走 expect/actual：Android 需 Context，
 * JVM/iOS 无参即可，调用方按平台构造。
 */
interface SourceClipboard {
    suspend fun getText(): String?
    suspend fun setText(text: String): Boolean
}

/** 测试与无剪贴板环境用的内存实现。 */
class FakeClipboard(var text: String? = null) : SourceClipboard {
    override suspend fun getText(): String? = text
    override suspend fun setText(text: String): Boolean {
        this.text = text
        return true
    }
}
