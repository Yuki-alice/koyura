package app.koyura.app.visual

/**
 * 18 页视觉矩阵与白皮书截图的对照键。
 *
 * `phoneAsset` / `tabletAsset` 是 `docs/design-whitepaper/assets/phone|tablet` 下的文件名，
 * 主 agent 生成报告时按这里的键把本测试产出的 PNG 与白皮书参考图配对。
 * 路径只作为字符串键保存，测试本身不读取这些 JPG（避免把白皮书资源变成测试依赖）。
 *
 * 本版全部页面都由真实装配渲染；T4.1 在 15 页基础上补 Feed、Migrate、data-storage 三页。
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
            slug = "onboarding",
            title = "引导 Onboarding",
            phoneAsset = "01_onboarding_theme.jpg",
            tabletAsset = "01_onboarding_theme.jpg",
            implemented = true,
            note = "真实 OnboardingRoute；平板无截图，按单栏实现（⚠️缺口）。",
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
            slug = "settings",
            title = "设置 Settings",
            phoneAsset = "18_settings_main.jpg",
            tabletAsset = "18_settings_main.jpg",
            implemented = true,
            note = "使用真实 SettingsRoute，宽屏展示双栏外观设置。",
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
            slug = "update-errors",
            title = "更新错误 Update errors",
            phoneAsset = "16_updates.jpg",
            tabletAsset = "15_updates.jpg",
            implemented = true,
            note = "真实 UtilityPageRoute(update-errors)，读 UpdatesRepository.failures()；白皮书无独立截图，键指向 Updates 图。",
        ),
        VisualPageCatalogEntry(
            slug = "about",
            title = "关于 / 新功能 About",
            phoneAsset = "27_settings_about.jpg",
            tabletAsset = "27_settings_about.jpg",
            implemented = true,
            note = "真实 UtilityPageRoute(about)；设置内的 About 子页仍是独立实现。",
        ),
        VisualPageCatalogEntry(
            slug = "browse-feed",
            title = "浏览 Feed",
            phoneAsset = "29_feed.jpg",
            tabletAsset = "29_feed.jpg",
            implemented = true,
            note = "BrowsePane.Feed，读 UpdatesRepository 的更新流。",
        ),
        VisualPageCatalogEntry(
            slug = "browse-migrate",
            title = "浏览 迁移 Migrate",
            phoneAsset = "06_browse_sources.jpg",
            tabletAsset = "36_migrate.jpg",
            implemented = true,
            note = "BrowsePane.Migrate 列已装源；手机无迁移截图，键指向源浏览图。",
        ),
        VisualPageCatalogEntry(
            slug = "utility-data-storage",
            title = "工具页 存储用量 Data storage",
            phoneAsset = "25_settings_data_storage.jpg",
            tabletAsset = "25_settings_data_storage.jpg",
            implemented = true,
            note = "真实 UtilityPageRoute(data-storage)，读书架快照计数；白皮书对应设置子页截图。",
        ),
        VisualPageCatalogEntry(
            slug = "secondary-tools",
            title = "次级工具页 Secondary tools (global search)",
            phoneAsset = "35_library_filter_sheet.jpg",
            tabletAsset = "35_library_filter_sheet.jpg",
            implemented = true,
            note = "以真实 GlobalSearchRoute 承载次级工具页；白皮书无同名截图，键指向最接近的弹层参考图。",
        ),
    )

    /** 生成 PNG 的文件名规则，与 VisualParityMatrixTest 写盘逻辑一致。 */
    fun generatedPng(slug: String, form: String, theme: String): String =
        "visual-$slug-$form-$theme.png"

    init {
        require(pages.size == 18) { "视觉矩阵必须恰好 18 页，当前 ${pages.size}" }
        require(pages.map { it.slug }.toSet().size == pages.size) { "slug 必须唯一" }
    }
}
