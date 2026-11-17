package app.koyura.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.drawBehind

/**
 * 全仓唯一的封面组件（书架 / 搜索行 / 历史行 / 详情头共用）。
 *
 * 契约：
 * - 输入为 `sourceKey + coverUrl + label`。`sourceKey` 参与加载键与缓存隔离，
 *   `coverUrl` 为空时零网络、直接占位。
 * - 取图由调用方经源会话 `comic.onThumbnailLoad` 单次回调（无 `onLoadFailed` 重试）
 *   或源 HTTP 直链（携带该源 Cookie 与请求头）完成，本组件只做单次 `load()` 调用，
 *   不重试、不预取整库。
 * - 成功字节经 `decode` 还原（调用方注入平台实现）；损坏字节解码失败即占位。
 * - 失败与空图一律回退 [KomikkuCoverPlaceholder] 首字母色块，保持调用方给定的
 *   固定尺寸与圆角，不改变布局。
 * - 内存与磁盘缓存按 sourceKey 隔离：调用方（书架会话）以
 *   `sourceKey + coverUrl` 为缓存键；磁盘层由数据轨实现，本组件不持有任何缓存。
 */
const val KomikkuCoverImageTag = "komikku-cover-image"
const val KomikkuCoverPlaceholderTag = "komikku-cover-placeholder"

/** 网格封面默认形态：宽高比 2:3，圆角 8dp。 */
const val KomikkuCoverAspectRatio: Float = 2f / 3f
val KomikkuCoverCornerRadius: Dp = 8.dp

private sealed interface CoverLoad {
    data object Idle : CoverLoad
    data object Loading : CoverLoad
    data class Ready(val image: ImageBitmap) : CoverLoad
    data object Fallback : CoverLoad
}

@Composable
fun KomikkuCover(
    sourceKey: String,
    coverUrl: String,
    label: String,
    load: (suspend () -> ByteArray)?,
    decode: (ByteArray) -> ImageBitmap?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = KomikkuCoverCornerRadius,
) {
    var state: CoverLoad by remember(sourceKey, coverUrl) { mutableStateOf(CoverLoad.Idle) }
    if (coverUrl.isBlank() || load == null) {
        KomikkuCoverPlaceholder(
            label = label,
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .testTag(KomikkuCoverPlaceholderTag),
        )
        return
    }
    LaunchedEffect(sourceKey, coverUrl) {
        state = CoverLoad.Loading
        val bytes = try {
            load()
        } catch (_: Exception) {
            null
        }
        state = if (bytes != null) {
            val image = try {
                decode(bytes)
            } catch (_: Exception) {
                null
            }
            if (image != null) CoverLoad.Ready(image) else CoverLoad.Fallback
        } else {
            CoverLoad.Fallback
        }
    }
    val current = state
    if (current is CoverLoad.Ready) {
        Image(
            bitmap = current.image,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .testTag(KomikkuCoverImageTag),
            alpha = DefaultAlpha,
            colorFilter = null,
        )
    } else {
        KomikkuCoverPlaceholder(
            label = label,
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .testTag(KomikkuCoverPlaceholderTag),
        )
    }
}

/**
 * 详情头模糊背景：缺图时只用主题 surface 色，成功时叠一层低透明封面。
 *
 * 从不做封面取色（无调色板提取即无崩溃来源），行为与上游缺图回退等价。
 */
@Composable
fun KomikkuCoverBackdrop(
    sourceKey: String,
    coverUrl: String,
    label: String,
    load: (suspend () -> ByteArray)?,
    decode: (ByteArray) -> ImageBitmap?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    var backdrop: ImageBitmap? by remember(sourceKey, coverUrl) { mutableStateOf(null) }
    if (!coverUrl.isBlank() && load != null) {
        LaunchedEffect(sourceKey, coverUrl) {
            backdrop = try {
                decode(load())
            } catch (_: Exception) {
                null
            }
        }
    }
    Box(
        modifier = modifier.drawBehind { drawRect(surface) },
    ) {
        val image = backdrop
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.24f),
            )
        }
        content()
    }
}

/** 封面图片的语义化尺寸（沿用现有三处占位尺寸，供调用方复用）。 */
object KomikkuCoverSizes {
    /** 搜索行封面（沿用 SourceScreens 占位 48x72）。 */
    val SearchRowCover: Dp = 48.dp
    val SearchRowCoverHeight: Dp = 72.dp

    /** 历史行封面（HistoryScreen 占位 56x80，行高 96dp）。 */
    val HistoryRowCover: Dp = 56.dp
    val HistoryRowCoverHeight: Dp = 80.dp

    /** 详情头封面（详情头占位 96x144）。 */
    val DetailHeaderCover: Dp = 96.dp
    val DetailHeaderCoverHeight: Dp = 144.dp
}
