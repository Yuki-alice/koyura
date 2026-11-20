package app.koyura.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score

/**
 * 从封面像素中提取主色，供详情页/阅读器的 `seedColor` 使用。
 *
 * 参考实现 `MangaCoverMetadata.kt` 用 Android `Palette` 算 `vibrantCoverColor` 并缓存；
 * 这里用 material-color-utilities 的 QuantizerCelebi + Score（同一套 HCT 打分），
 * 得到等价的 vibrant 主色，且不依赖 Android 平台 API。
 *
 * [pixels] 为 ARGB 打包的整数数组，由调用方从现有封面字节管线解码得到；
 * 本函数不发起网络请求、不改变按源会话取图的行为。取不到有效颜色时返回 null，
 * 调用方据此回退到静态主题。
 */
fun extractCoverSeedColor(pixels: IntArray, maxColors: Int = 128): Color? {
    if (pixels.isEmpty()) return null
    val quantized = QuantizerCelebi.quantize(pixels, maxColors)
    if (quantized.isEmpty()) return null
    val ranked = Score.score(quantized, 1)
    val argb = ranked.firstOrNull() ?: return null
    return Color(argb)
}
