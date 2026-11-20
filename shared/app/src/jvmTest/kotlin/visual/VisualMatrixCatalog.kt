package app.koyura.app.visual

/**
 * 15 页视觉矩阵与白皮书截图的对照键。
 *
 * `phoneAsset` / `tabletAsset` 是 `docs/design-whitepaper/assets/phone|tablet` 下的文件名，
 * 主 agent 生成报告时按这里的键把本测试产出的 PNG 与白皮书参考图配对。
 * 路径只作为字符串键保存，测试本身不读取这些 JPG（避免把白皮书资源变成测试依赖）。
 *
 * `implemented = false` 的四页在测试中渲染为明确标注的「未实现占位」画面，
 * 不冒充真实页面；对照键仍给出白皮书里最接近的参考图，便于报告说明差距。
 */
data class VisualPageCatalogEntry(
    /** 生成 PNG 用的稳定 slug：visual-<slug>-<narrow|wide>-<light|dark>.png */
    val slug: String,
    /** 人类可读页名。 */
    val title: String,
    /** docs/design-whitepaper/assets/phone 下的文件名。 */
    val phoneAsset: String,
    /** docs/design-whitepaper/assets/tablet 下的文件名。 */
    val tabletAsset: String,
    /** 该页是否由真实装配渲染（false 表示确定性占位画面）。 */
    val implemented: Boolean,
    /** 需要报告补充说明时填写。 */
    val note: String = "",
)

object VisualMatrixCatalog {
    val pages: List<VisualPageCatalogEntry> = listOf(
        VisualPageCatalogEntry(
            slug = "onboarding-placeholder",
            title = "引导 Onboarding",
            phoneAsset = "01_onboarding_theme.jpg",
            tabletAsset = "01_onboarding_theme.jpg",
            implemented = false,
            note = "引导流程尚无路由；占位画面明确标注未实现。",
        ),
        VisualPageCatalogEntry(
            slug = "library",
            title = "书架 Library",
            phoneAsset = "10_library_grid.jpg",
            tabletAsset = "10_library_grid.jpg",
            implemented = true,
        ),
        VisualPageCatalogEntry(
            slug = "browse",
            title = "浏览 Browse",
            phoneAsset = "06_browse_sources.jpg",
            tabletAsset = "06_browse_sources.jpg",
            implemented = true,
        ),
        VisualPageCatalogEntry(
            slug = "detail",
            title = "详情 Detail",
            phoneAsset = "11_manga_detail_library.jpg",
            tabletAsset = "11_manga_detail.jpg",
            implemented = true,
        ),
        VisualPageCatalogEntry(
            slug = "reader",
            title = "阅读器 Reader",
            phoneAsset = "13_reader.jpg",
            tabletAsset = "12_reader.jpg",
            implemented = true,
        ),
        VisualPageCatalogEntry(
            slug = "history",
            title = "历史 History",
            phoneAsset = "15_history.jpg",
            tabletAsset = "14_history.jpg",
            implemented = true,
        ),
        VisualPageCatalogEntry(
            slug = "updates",
            title = "更新 Updates",
            phoneAsset = "16_updates.jpg",
            tabletAsset = "15_updates.jpg",
            implemented = true,
        ),
        VisualPageCatalogEntry(
            slug = "more",
            title = "更多 More",
            phoneAsset = "17_more.jpg",
            tabletAsset = "16_more.jpg",
            implemented = true,
        ),
        VisualPageCatalogEntry(
            slug = "settings-placeholder",
            title = "设置 Settings",
            phoneAsset = "18_settings_main.jpg",
            tabletAsset = "18_settings_main.jpg",
            implemented = false,
            note = "设置页尚无顶层路由；占位画面明确标注未实现。",
        ),
        VisualPageCatalogEntry(
            slug = "statistics",
            title = "统计 Statistics",
            phoneAsset = "30_statistics.jpg",
            tabletAsset = "30_statistics.jpg",
            implemented = true,
        ),
        VisualPageCatalogEntry(
            slug = "categories",
            title = "分类 Categories",
            phoneAsset = "31_categories.jpg",
            tabletAsset = "31_categories.jpg",
            implemented = true,
        ),
        VisualPageCatalogEntry(
            slug = "download-queue",
            title = "下载队列 Download queue",
            phoneAsset = "32_download_queue.jpg",
            tabletAsset = "32_download_queue.jpg",
            implemented = true,
        ),
        VisualPageCatalogEntry(
            slug = "update-errors-placeholder",
            title = "更新错误 Update errors",
            phoneAsset = "16_updates.jpg",
            tabletAsset = "15_updates.jpg",
            implemented = false,
            note = "更新错误是 Updates 的溢出态而非独立路由；白皮书无独立截图，键指向 Updates 图并明确标注占位。",
        ),
        VisualPageCatalogEntry(
            slug = "about-placeholder",
            title = "关于 / 新功能 About",
            phoneAsset = "27_settings_about.jpg",
            tabletAsset = "27_settings_about.jpg",
            implemented = false,
            note = "关于/新功能目前只是 More 的一行；占位画面明确标注未实现。",
        ),
        VisualPageCatalogEntry(
            slug = "secondary-tools",
            title = "次级工具页 Secondary tools (global search)",
            phoneAsset = "29_feed.jpg",
            tabletAsset = "29_feed.jpg",
            implemented = true,
            note = "以真实 GlobalSearchRoute 承载次级工具页；白皮书无同名截图，键指向最接近的次级工具参考图。",
        ),
    )

    /** 生成 PNG 的文件名规则，与 VisualParityMatrixTest 写盘逻辑一致。 */
    fun generatedPng(slug: String, form: String, theme: String): String =
        "visual-$slug-$form-$theme.png"

    init {
        require(pages.size == 15) { "视觉矩阵必须恰好 15 页，当前 ${pages.size}" }
        require(pages.map { it.slug }.toSet().size == pages.size) { "slug 必须唯一" }
    }
}
