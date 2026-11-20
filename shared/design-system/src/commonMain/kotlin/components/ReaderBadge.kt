package app.koyura.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyura.designsystem.DesignTokens

/** 徽标语义色：下载数用 [Tertiary]，未读数/普通计数用 [Secondary]，缺失源用 [ErrorContainer]。 */
enum class ReaderBadgeTone {
    Secondary,
    Tertiary,
    Error,
    ErrorContainer,
}

/**
 * 18dp 徽标，不撑高宿主卡片。
 *
 * 复刻 Komikku `Badges.kt`：高度 [DesignTokens.Sizes.BadgeHeight]=18dp、
 * clip extraSmall(4dp)、padding 3dp/1dp、bodySmall 12sp Medium。
 * 颜色按 [tone] 取语义色对，默认 secondary/onSecondary。
 *
 * 白皮书出处：06 §6.4 徽标表、9.5 #14。
 */
@Composable
fun ReaderBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: ReaderBadgeTone = ReaderBadgeTone.Secondary,
) {
    val (containerColor, contentColor) = when (tone) {
        ReaderBadgeTone.Secondary ->
            MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
        ReaderBadgeTone.Tertiary ->
            MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
        ReaderBadgeTone.Error ->
            MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        ReaderBadgeTone.ErrorContainer ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }

    Box(
        modifier = modifier
            .height(DesignTokens.Sizes.BadgeHeight)
            .clip(RoundedCornerShape(DesignTokens.Radius.ExtraSmall))
            .background(containerColor)
            .padding(DesignTokens.Sizes.BadgeContentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

/**
 * 全圆角药丸：`surfaceContainerHigh` / `onSurface`，padding 6dp/1dp（左外边距由调用方 modifier 控制）。
 *
 * [countStyle] 为 true 时切到计数形态：10sp、`onBackground` 8%（亮）/12%（暗）叠加，
 * 对应 Tab 上的徽标计数（06 §6.3/§6.4）。
 *
 * 白皮书出处：06 §6.4 Pill / 计数 Pill、02 §2.7 alpha。
 */
@Composable
fun ReaderPill(
    text: String,
    modifier: Modifier = Modifier,
    countStyle: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (countStyle) {
        val alpha = if (scheme.surface.luminance() < 0.5f) {
            DesignTokens.Alpha.PillCountDark
        } else {
            DesignTokens.Alpha.PillCountLight
        }
        scheme.onBackground.copy(alpha = alpha)
    } else {
        scheme.surfaceContainerHigh
    }
    val textStyle = if (countStyle) {
        MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
    } else {
        MaterialTheme.typography.bodySmall
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(PillContentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = scheme.onSurface,
            style = textStyle,
            maxLines = 1,
        )
    }
}

// Pill 内边距 6dp/1dp（白皮书 06 §6.4）。
private val PillContentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp)
