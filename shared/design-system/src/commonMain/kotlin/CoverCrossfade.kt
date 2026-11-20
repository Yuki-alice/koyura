package app.koyura.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import app.koyura.designsystem.components.UiTheme
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val LocalCoverFrameLoader = staticCompositionLocalOf<ImageLoader?> { null }

/** One renderer for the shelf. The scoped fetcher owns decoding and delegates source I/O. */
@Composable
fun CrossfadingCoverScope(content: @Composable () -> Unit) {
    val context = LocalPlatformContext.current
    val renderer = remember(context) {
        ImageLoader.Builder(context)
            .components { add(CoverFetcherFactory) }
            .memoryCache(null)
            .diskCache(null)
            .build()
    }
    DisposableEffect(renderer) { onDispose { renderer.shutdown() } }
    CompositionLocalProvider(LocalCoverFrameLoader provides renderer, content = content)
}

@Composable
fun CrossfadingCover(
    sourceKey: String,
    coverUrl: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val renderer = LocalCoverFrameLoader.current
    val loader = LocalCoverImageLoader.current
    val decoder = LocalCoverDecoder.current
    val cache = LocalCoverMemoryCache.current
    val context = LocalPlatformContext.current
    if (renderer == null || loader == null || decoder == null || coverUrl.isBlank()) {
        CoverPlaceholder(label, modifier.testTag(CoverPlaceholderTag))
        return
    }
    val request = remember(sourceKey, coverUrl, renderer, loader, decoder, cache, context) {
        ImageRequest.Builder(context)
            .data(CoverRequest(sourceKey, coverUrl, loader, decoder, cache))
            .crossfade(DesignTokens.Motion.ImageCrossfadeMs)
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request, imageLoader = renderer, contentScale = ContentScale.Crop)
    val state by painter.state.collectAsState()
    val loaded = (state as? AsyncImagePainter.State.Success)?.result?.request?.data == request.data
    var revealComplete by remember(request) { mutableStateOf(false) }
    LaunchedEffect(request, loaded) {
        revealComplete = false
        if (loaded) {
            delay(DesignTokens.Motion.ImageCrossfadeMs.toLong())
            revealComplete = true
        }
    }
    Box(modifier.clip(RoundedCornerShape(CoverCornerRadius)).background(UiTheme.colorScheme.surfaceVariant)) {
        if (!revealComplete) {
            val placeholder = Modifier.matchParentSize()
            CoverPlaceholder(label, if (loaded) placeholder else placeholder.testTag(CoverPlaceholderTag))
        }
        if (loaded) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.matchParentSize().testTag(CoverImageTag),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private class CoverRequest(
    val sourceKey: String,
    val url: String,
    val loader: CoverImageLoader,
    val decoder: CoverDecoder,
    val cache: CoverMemoryCache,
)

private object CoverFetcherFactory : Fetcher.Factory<CoverRequest> {
    override fun create(data: CoverRequest, options: Options, imageLoader: ImageLoader): Fetcher = Fetcher {
        val bytes = withContext(Dispatchers.Main.immediate) {
            loadCachedCoverBytes(data.loader, data.cache, data.sourceKey, data.url)
        } ?: return@Fetcher null
        val bitmap = data.decoder.decode(bytes) ?: return@Fetcher null
        ImageFetchResult(bitmap.toCoverImage(), isSampled = false, dataSource = DataSource.MEMORY)
    }
}

internal expect fun ImageBitmap.toCoverImage(): coil3.Image
