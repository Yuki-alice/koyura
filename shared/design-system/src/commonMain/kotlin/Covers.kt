package app.koyura.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag

/**
 * 全仓唯一的封面组件（A 轨所有）。
 *
 * 输入为 [sourceKey] + [coverUrl] + [label] 三元组：
 * - 取图只能经 [CoverImageLoader]（生产实现走 Venera 源会话的 `comic.onThumbnailLoad`
 *   回调或该源 HTTP 直链并携带该源 Cookie 与请求头）；
 * - 不同源的内存缓存按 [sourceKey] 隔离（[CoverMemoryCache]），磁盘键见
 *   [coverDiskKey]（同命名空间预留，落盘由数据层实现）；
 * - 缩略图沿用上游单次无重试语义：每个 `sourceKey|url` 只尝试一次，失败即回退占位；
 * - 列表只在 item 被组合（可视区 + Compose 预取）时触发加载，禁止长列表全量预取；
 * - URL 为空或加载/解码失败时回退首字母色块占位，保持调用方固定尺寸与 8dp 圆角。
 */
fun interface CoverImageLoader {
    /**
     * 单次加载封面字节，失败返回 null（调用方不重试）。
     *
     * 生产实现必须经 Venera 源会话的 `comic.onThumbnailLoad` 回调，缺回调时才允许源
     * HTTP 直链并携带该源 Cookie 与请求头。
     */
    suspend fun load(sourceKey: String, coverUrl: String): ByteArray?
}

/** 把封面字节解码为 [ImageBitmap]，不支持的字节返回 null（回退占位，不崩溃）。 */
fun interface CoverDecoder {
    fun decode(bytes: ByteArray): ImageBitmap?
}

/** 当前的封面加载器；未提供时所有封面直接回退占位（离线/预览安全）。 */
val LocalCoverImageLoader = staticCompositionLocalOf<CoverImageLoader?> { null }

/** 当前的封面解码器；未提供时所有封面直接回退占位。 */
val LocalCoverDecoder = staticCompositionLocalOf<CoverDecoder?> { null }

/**
 * 按源隔离的内存封面缓存：`sourceKey -> (url -> bytes)`，每源 LRU 上限。
 *
 * 不同源的条目永不共享；源 key 变更/卸载后调用 [evictSource] 丢弃该源全部缓存。
 */
class CoverMemoryCache(private val maxPerSource: Int = 128) {
    private val perSource = mutableMapOf<String, LinkedHashMap<String, ByteArray>>()

    fun get(sourceKey: String, coverUrl: String): ByteArray? =
        perSource[sourceKey]?.get(coverUrl)

    fun put(sourceKey: String, coverUrl: String, bytes: ByteArray) {
        val bucket = perSource.getOrPut(sourceKey) { LinkedHashMap() }
        bucket[coverUrl] = bytes
        while (bucket.size > maxPerSource) {
            val eldest = bucket.keys.firstOrNull() ?: break
            bucket.remove(eldest)
        }
    }

    fun evictSource(sourceKey: String) {
        perSource.remove(sourceKey)
    }

    fun clear() {
        perSource.clear()
    }

    fun cachedUrls(sourceKey: String): Set<String> =
        perSource[sourceKey]?.keys?.toSet().orEmpty()
}

/** 进程默认的内存封面缓存。 */
val DefaultCoverMemoryCache = CoverMemoryCache()

/** 当前生效的内存封面缓存，测试可替换为独立实例以断言隔离语义。 */
val LocalCoverMemoryCache = staticCompositionLocalOf { DefaultCoverMemoryCache }

/**
 * 磁盘缓存键（预留）：`sourceKey` 命名空间 + URL 哈希，保证不同源即使同一 URL
 * 也落在不同键上。落盘实现由数据层按此键执行，本轨只定命名。
 */
fun coverDiskKey(sourceKey: String, coverUrl: String): String {
    val safeSource = sourceKey.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")
    val hash = coverUrl.hashCode().toUInt().toString(16)
    return "cover/${safeSource.ifEmpty { "_" }}/$hash.img"
}

/** Test tag of the loaded cover art (per-row disambiguation stays with the caller). */
const val CoverImageTag = "komikku-cover-image"

/** Test tag of the letter placeholder shown while loading, empty or failed. */
const val CoverPlaceholderTag = "komikku-cover-placeholder"

/**
 * 全仓封面统一圆角（网格 2:3、搜索行 48x72、历史行 56x80、详情头 96x144 均用此值）。
 *
 * 尺寸由调用方 modifier 决定，本常量只定圆角。白皮书 02 §2.7 圆角 extraSmall(4dp)，
 * Code: MangaCover.kt:62,175 默认 `shape = MaterialTheme.shapes.extraSmall`。
 */
val CoverCornerRadius = DesignTokens.Radius.ExtraSmall

/**
 * 加载一封面的解码位图（单次尝试，可空）：头部大图与模糊背景共用同一结果，
 * 缓存命中时不触发加载器。
 */
@Composable
fun rememberCoverBitmap(sourceKey: String, coverUrl: String): ImageBitmap? {
    // Remember slots always run so blank/non-blank transitions keep composition order stable.
    var bytes by remember(sourceKey, coverUrl) { mutableStateOf<ByteArray?>(null) }
    var primed by remember(sourceKey, coverUrl) { mutableStateOf(false) }
    var failed by remember(sourceKey, coverUrl) { mutableStateOf(false) }
    val loader = LocalCoverImageLoader.current
    val decoder = LocalCoverDecoder.current
    val cache = LocalCoverMemoryCache.current
    if (coverUrl.isBlank() || loader == null || decoder == null) return null
    if (!primed) {
        primed = true
        bytes = cache.get(sourceKey, coverUrl)
    }
    if (bytes == null && !failed) {
        LaunchedEffect(sourceKey, coverUrl) {
            val loaded = try {
                loader.load(sourceKey, coverUrl)
            } catch (_: Exception) {
                null
            }
            if (loaded != null) {
                cache.put(sourceKey, coverUrl, loaded)
                bytes = loaded
            } else {
                failed = true
            }
        }
    }
    val snapshot = bytes
    return remember(snapshot) {
        snapshot?.let { runCatching { decoder.decode(it) }.getOrNull() }
    }
}

/**
 * 共用封面：有图显示 8dp 圆角裁剪图，无图/加载中/失败显示首字母占位。
 *
 * 尺寸完全由调用方 [modifier] 决定（搜索行 48x72、历史行 56x80、详情头 96x144，
 * 网格 2:3），组件内部不改尺寸以防布局跳动。
 */
@Composable
fun Cover(
    sourceKey: String,
    coverUrl: String,
    label: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val bitmap = rememberCoverBitmap(sourceKey, coverUrl)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(RoundedCornerShape(CoverCornerRadius))
                .testTag(CoverImageTag),
        )
    } else {
        CoverPlaceholder(
            label = label,
            modifier = modifier.testTag(CoverPlaceholderTag),
        )
    }
}

/**
 * 详情头模糊背景：封面经 surfaceTint 衬底 + 模糊 + 低透明度铺底，上覆透明→surface
 * 渐隐（起点 50% 高度），保证文字可读；缺图时直接使用主题 surface 色。
 *
 * Code: MangaInfoHeader.kt:147-184（`surfaceTint.copy(alpha = 0.4f)` + `.blur(7.dp)`
 * + `.alpha(0.2f)`，渐变 `startY = size.height / 2`）；白皮书 07 §7.4 背景层。
 */
@Composable
fun DetailBackdrop(
    cover: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = DesignTokens.Alpha.Backdrop,
                modifier = Modifier.matchParentSize()
                    .background(
                        MaterialTheme.colorScheme.surfaceTint.copy(
                            alpha = DesignTokens.Alpha.SurfaceTintWash,
                        ),
                    )
                    .blur(DesignTokens.Sizes.BackdropBlur),
            )
            Box(
                modifier = Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0.5f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.surface,
                    ),
                ),
            )
        }
    }
}
