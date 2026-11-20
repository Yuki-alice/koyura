package app.koyura.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle

/**
 * 主题设置项需要的能力，以纯数据契约暴露给功能层。
 *
 * 本轮不接生产持久化：功能层定义仓储接口与 Fake 实现，生产桥接留给主 agent。
 * 默认值参考 Komikku `UiPreferences`：`coverBased` 默认开、封面取色风格 Vibrant、
 * 自定义种子主题风格 Fidelity、AMOLED 默认关、模式 SYSTEM。
 */
data class ThemeSelection(
    val preset: ThemePreset = ThemePreset.DEFAULT,
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val coverBased: Boolean = true,
    val coverBasedStyle: PaletteStyle = PaletteStyle.Vibrant,
    val customSeedColor: Color? = null,
    val customStyle: PaletteStyle = PaletteStyle.Fidelity,
) {
    /** 该选择当前生效的封面种子色；关闭封面取色时为 null。 */
    fun effectiveSeedColor(coverSeed: Color?): Color? = when {
        customSeedColor != null -> customSeedColor
        coverBased -> coverSeed
        else -> null
    }
}
