package app.koyura.features.settings

import androidx.compose.ui.graphics.Color
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.theme.ThemeMode
import app.koyura.designsystem.theme.ThemePreset

/**
 * 设置页的数据契约（纯 UI 状态）。
 *
 * 本轮不接生产持久化：所有字段都是可安全复制的普通值，仓储只有 load/save 两个同步操作，
 * 由 [FakeSettingsRepository] 在内存中持有。生产桥接与真正的偏好存储由装配层后续接入，
 * 契约里的 [CoverBlendStyle.paletteStyleName] 可直接映射到 materialkolor 的 PaletteStyle。
 */
enum class SettingsSubpage(
    val label: String,
    val summary: String,
    val icon: ActionIcon,
) {
    APPEARANCE("Appearance", "Theme, display and language", ActionIcon.Layout),
    LIBRARY("Library", "Categories, updates and display", ActionIcon.Repository),
    READER("Reader", "Reading mode and page layout", ActionIcon.ModeWebtoon),
    DOWNLOADS("Downloads", "Directory, network and queue", ActionIcon.Download),
    TRACKING("Tracking", "Tracking services", ActionIcon.Sync),
    CONNECTIONS("Connections", "Sync and remote services", ActionIcon.Public),
    BROWSE("Browse", "Sources and search", ActionIcon.Source),
    DATA_STORAGE("Data & storage", "Backup, storage and cache", ActionIcon.Storage),
    ADVANCED("Advanced", "Debug, logs and updates", ActionIcon.Settings),
    ABOUT("About", "Version and licenses", ActionIcon.Info),
}

/**
 * 封面取色 / 自定义主题的调色风格。
 *
 * 名字与 materialkolor `PaletteStyle.entries` 一一对应：[paletteStyleName] 保留原名，
 * 装配层可用 `PaletteStyle.valueOf(name)` 直接还原，无需在本模块引入 materialkolor 类型。
 */
enum class CoverBlendStyle(
    val label: String,
    val paletteStyleName: String,
) {
    TONAL_SPOT("Tonal spot", "TonalSpot"),
    NEUTRAL("Neutral", "Neutral"),
    VIBRANT("Vibrant", "Vibrant"),
    EXPRESSIVE("Expressive", "Expressive"),
    RAINBOW("Rainbow", "Rainbow"),
    FRUIT_SALAD("Fruit salad", "FruitSalad"),
    MONOCHROME("Monochrome", "Monochrome"),
    FIDELITY("Fidelity", "Fidelity"),
    CONTENT("Content", "Content"),
}

enum class SettingsDateFormat(val label: String) {
    RELATIVE("Relative"),
    ABSOLUTE("Absolute"),
}

/** 一屏设置页的全部可持久化值；默认值与白皮书 7.9 / ThemeSelection 的默认值一致。 */
data class SettingsPreferences(
    // Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themePreset: ThemePreset = ThemePreset.DEFAULT,
    val amoled: Boolean = false,
    val coverBased: Boolean = true,
    val coverBasedStyle: CoverBlendStyle = CoverBlendStyle.VIBRANT,
    val customSeedColor: Color? = null,
    val customStyle: CoverBlendStyle = CoverBlendStyle.FIDELITY,
    val relativeTime: Boolean = true,
    val dateFormat: SettingsDateFormat = SettingsDateFormat.RELATIVE,
    val language: String = "System",
    // Library
    val globalUpdate: Boolean = true,
    val updateRestrictions: Boolean = false,
    val libraryColumns: Int = 2,
    val showCategories: Boolean = true,
    val sortByTitle: Boolean = false,
    // Reader
    val readerMode: String = "Webtoon",
    val readingDirection: String = "Vertical",
    val cropBorders: Boolean = false,
    val autoScroll: Boolean = false,
    val screenBrightness: Float = 1f,
    // Downloads
    val downloadDirectory: String = "Default directory",
    val parallelDownloads: Int = 3,
    val wifiOnly: Boolean = true,
    val autoDownload: Boolean = false,
    // Tracking / Connections / Browse
    val autoUpdateTracking: Boolean = true,
    val syncEnabled: Boolean = false,
    val multiSourceSearch: Boolean = false,
    val showAdultSources: Boolean = false,
    // Data & storage / Advanced
    val backupFrequency: String = "Weekly",
    val debugEnabled: Boolean = false,
    val logLevel: String = "Normal",
    val updateChannel: String = "Stable",
)

/** 对外暴露的不可变 UI 状态快照。 */
data class SettingsUiState(
    val preferences: SettingsPreferences = SettingsPreferences(),
    val selectedSubpage: SettingsSubpage = SettingsSubpage.APPEARANCE,
)

/**
 * 设置仓储：只负责整份偏好的读回与写回。
 *
 * 生产实现可以把它映射到任意偏好存储；本轮 Fake 用内存持有，不触碰 domain/data 层。
 */
interface SettingsRepository {
    fun load(): SettingsPreferences
    fun save(preferences: SettingsPreferences)
}
