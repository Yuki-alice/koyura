package app.koyura.designsystem

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.koyura.designsystem.resources.Res
import app.koyura.designsystem.resources.nav_library_selected
import org.jetbrains.compose.resources.painterResource

/**
 * 品牌占位字形：复用导航书架字形，64dp 下渲染，onSurface 染色。
 *
 * 真实品牌视觉定稿前 More 页 Logo 区与启动位共用本占位；定稿后只改这一处。
 */
@Composable
fun BrandPlaceholder(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Icon(
        painter = painterResource(Res.drawable.nav_library_selected),
        contentDescription = null,
        tint = tint,
        modifier = modifier,
    )
}
