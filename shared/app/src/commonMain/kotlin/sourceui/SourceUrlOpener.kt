package app.koyura.app.sourceui

/** 系统浏览器打开链接（注册外链与脚本 launchUrl 的宿主侧实现）。 */
fun interface SourceUrlOpener {
    suspend fun open(url: String): Boolean
}

/** 测试与无浏览器环境的记录实现。 */
class FakeUrlOpener(
    val opened: MutableList<String> = mutableListOf(),
    var result: Boolean = true,
) : SourceUrlOpener {
    override suspend fun open(url: String): Boolean {
        if (result) opened += url
        return result
    }
}
