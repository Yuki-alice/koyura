package app.koyura.app.library

import app.koyura.designsystem.theme.ThemeMode
import app.koyura.designsystem.theme.ThemePreset
import app.koyura.domain.appearance.AppearancePreferences
import app.koyura.domain.appearance.ThemeMode as DomainThemeMode
import app.koyura.features.settings.SettingsPreferences
import app.koyura.features.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 把 `ProductionSettingsRepository`（suspend、按 section 存取）桥到设置页的同步契约。
 *
 * 设置页的 [SettingsRepository] 只有同步 `load()/save()`：这里用内存快照顶住同步读，
 * 启动时 [hydrate] 从生产仓储拉一次真实值，之后每次 `save()` 先更新快照再异步写回。
 * 仅映射当前生产仓储真实拥有的两段：外观（mode/themeId/amoled/useCoverColors）与阅读器偏好
 * （由 `readerPreferences = settings` 直接消费，不经本类）；其余字段仍是 UI 默认值，
 * 待各 section 落地后再逐段接上（避免伪造持久化）。
 */
internal class BridgedSettingsRepository(
    private val production: ProductionSettingsRepository,
    private val scope: CoroutineScope,
) : SettingsRepository {
    private var snapshot = SettingsPreferences()

    /** 从生产仓储读取一次真实外观并覆盖快照；返回合并后的完整偏好。 */
    suspend fun hydrate(): SettingsPreferences {
        val appearance = production.appearance()
        snapshot = snapshot.copy(
            themeMode = appearance.mode.toDesignMode(),
            themePreset = presetOf(appearance.themeId),
            amoled = appearance.amoled,
            coverBased = appearance.useCoverColors,
        )
        return snapshot
    }

    override fun load(): SettingsPreferences = snapshot

    override fun save(preferences: SettingsPreferences) {
        snapshot = preferences
        scope.launch {
            production.setAppearance(
                AppearancePreferences(
                    mode = preferences.themeMode.toDomainMode(),
                    themeId = preferences.themePreset.name.lowercase(),
                    amoled = preferences.amoled,
                    useCoverColors = preferences.coverBased,
                ),
            )
        }
    }
}

/** `themeId` 与 [ThemePreset] 的枚举名互相映射；未知 id 回退默认主题。 */
internal fun presetOf(themeId: String): ThemePreset =
    ThemePreset.entries.firstOrNull { it.name.equals(themeId, ignoreCase = true) } ?: ThemePreset.DEFAULT

/** 领域层的主题模式 → 设计系统枚举（两处同名但不同模块，必须显式转换）。 */
internal fun DomainThemeMode.toDesignMode(): ThemeMode = when (this) {
    DomainThemeMode.SYSTEM -> ThemeMode.SYSTEM
    DomainThemeMode.LIGHT -> ThemeMode.LIGHT
    DomainThemeMode.DARK -> ThemeMode.DARK
}

internal fun ThemeMode.toDomainMode(): DomainThemeMode = when (this) {
    ThemeMode.SYSTEM -> DomainThemeMode.SYSTEM
    ThemeMode.LIGHT -> DomainThemeMode.LIGHT
    ThemeMode.DARK -> DomainThemeMode.DARK
}
