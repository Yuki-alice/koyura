package app.koyura.designsystem

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.koyura.designsystem.resources.Res
import app.koyura.designsystem.resources.action_back
import app.koyura.designsystem.resources.action_batch_add
import app.koyura.designsystem.resources.action_calendar
import app.koyura.designsystem.resources.action_categories
import app.koyura.designsystem.resources.action_chapters
import app.koyura.designsystem.resources.action_checklist
import app.koyura.designsystem.resources.action_close
import app.koyura.designsystem.resources.action_cloud_off
import app.koyura.designsystem.resources.action_collapse
import app.koyura.designsystem.resources.action_crop
import app.koyura.designsystem.resources.action_crop_off
import app.koyura.designsystem.resources.action_delete
import app.koyura.designsystem.resources.action_download
import app.koyura.designsystem.resources.action_expand
import app.koyura.designsystem.resources.action_favorite
import app.koyura.designsystem.resources.action_favorite_border
import app.koyura.designsystem.resources.action_filter
import app.koyura.designsystem.resources.action_help
import app.koyura.designsystem.resources.action_hourglass
import app.koyura.designsystem.resources.action_incognito
import app.koyura.designsystem.resources.action_info
import app.koyura.designsystem.resources.action_invert_selection
import app.koyura.designsystem.resources.action_layout
import app.koyura.designsystem.resources.action_mode_continuous
import app.koyura.designsystem.resources.action_mode_left_to_right
import app.koyura.designsystem.resources.action_mode_right_to_left
import app.koyura.designsystem.resources.action_mode_vertical
import app.koyura.designsystem.resources.action_mode_webtoon
import app.koyura.designsystem.resources.action_more
import app.koyura.designsystem.resources.action_next
import app.koyura.designsystem.resources.action_pin
import app.koyura.designsystem.resources.action_pinned
import app.koyura.designsystem.resources.action_play
import app.koyura.designsystem.resources.action_previous
import app.koyura.designsystem.resources.action_public
import app.koyura.designsystem.resources.action_repository
import app.koyura.designsystem.resources.action_schedule
import app.koyura.designsystem.resources.action_search
import app.koyura.designsystem.resources.action_select_all
import app.koyura.designsystem.resources.action_settings
import app.koyura.designsystem.resources.action_source
import app.koyura.designsystem.resources.action_statistics
import app.koyura.designsystem.resources.action_storage
import app.koyura.designsystem.resources.action_sync
import app.koyura.designsystem.resources.action_update_errors
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

enum class ActionIcon(internal val resource: DrawableResource) {
    Back(Res.drawable.action_back),
    BatchAdd(Res.drawable.action_batch_add),
    Calendar(Res.drawable.action_calendar),
    Categories(Res.drawable.action_categories),
    CloudOff(Res.drawable.action_cloud_off),
    Search(Res.drawable.action_search),
    Filter(Res.drawable.action_filter),
    More(Res.drawable.action_more),
    Favorite(Res.drawable.action_favorite),
    FavoriteBorder(Res.drawable.action_favorite_border),
    Pin(Res.drawable.action_pin),
    Pinned(Res.drawable.action_pinned),
    Checklist(Res.drawable.action_checklist),
    SelectAll(Res.drawable.action_select_all),
    InvertSelection(Res.drawable.action_invert_selection),
    Repository(Res.drawable.action_repository),
    Public(Res.drawable.action_public),
    Schedule(Res.drawable.action_schedule),
    Hourglass(Res.drawable.action_hourglass),
    Incognito(Res.drawable.action_incognito),
    Info(Res.drawable.action_info),
    Previous(Res.drawable.action_previous),
    UpdateErrors(Res.drawable.action_update_errors),
    Next(Res.drawable.action_next),
    Settings(Res.drawable.action_settings),
    Statistics(Res.drawable.action_statistics),
    Storage(Res.drawable.action_storage),
    Expand(Res.drawable.action_expand),
    Help(Res.drawable.action_help),
    Collapse(Res.drawable.action_collapse),
    Source(Res.drawable.action_source),
    Close(Res.drawable.action_close),
    Delete(Res.drawable.action_delete),
    Sync(Res.drawable.action_sync),
    Play(Res.drawable.action_play),
    Download(Res.drawable.action_download),
    Chapters(Res.drawable.action_chapters),
    Layout(Res.drawable.action_layout),
    Crop(Res.drawable.action_crop),
    CropOff(Res.drawable.action_crop_off),
    ModeLeftToRight(Res.drawable.action_mode_left_to_right),
    ModeRightToLeft(Res.drawable.action_mode_right_to_left),
    ModeVertical(Res.drawable.action_mode_vertical),
    ModeWebtoon(Res.drawable.action_mode_webtoon),
    ModeContinuous(Res.drawable.action_mode_continuous),
}

@Composable
fun ActionIcon(
    icon: ActionIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(painterResource(icon.resource), contentDescription, modifier, tint)
}

@Composable
fun ActionIconButton(
    icon: ActionIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = LocalContentColor.current,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(contentColor = contentColor),
    ) {
        ActionIcon(icon, label)
    }
}
