package app.koyura.app.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import app.koyura.designsystem.CoverDecoder
import app.koyura.designsystem.CoverImageLoader
import app.koyura.designsystem.LocalCoverDecoder
import app.koyura.designsystem.LocalCoverImageLoader
import app.koyura.features.reader.comic.decodeToImageBitmap

/**
 * 把 [BrowseService.loadCover] 接到共用封面组件的单次加载器。
 *
 * 单次无重试语义由调用方（`Cover`/`rememberCoverBitmap`）保证：同一
 * `sourceKey|url` 在一次组合中只调一次，失败即占位；跨组合命中内存缓存不再调服务。
 */
fun browseCoverLoader(service: BrowseService?): CoverImageLoader? {
    if (service == null) return null
    return CoverImageLoader { sourceKey, coverUrl ->
        try {
            service.loadCover(sourceKey, coverUrl)
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * 应用层封面解码器：复用阅读器同一份平台解码（`decodeToImageBitmap`），
 * 不支持的字节返回 null 以便回退占位，永不抛到组合外。
 */
val AppCoverDecoder = CoverDecoder { bytes ->
    runCatching { bytes.decodeToImageBitmap() }.getOrNull()
}

/**
 * 为一屏提供封面管线（加载器 + 解码器 + 默认按源隔离内存缓存）。
 *
 * 生产字节通路（`comic.onThumbnailLoad`）由 [BrowseService.loadCover] 的服务实现提供；
 * 未接线时加载器为 null，封面保持占位，页面其余部分不受影响。
 */
@Composable
fun ProvideCoverPipeline(
    service: BrowseService?,
    content: @Composable () -> Unit,
) {
    val loader = remember(service) { browseCoverLoader(service) }
    CompositionLocalProvider(
        LocalCoverImageLoader provides loader,
        LocalCoverDecoder provides AppCoverDecoder,
        content = content,
    )
}
