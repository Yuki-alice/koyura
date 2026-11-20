package app.koyura.features.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.AppButton
import app.koyura.designsystem.components.AppSwitch
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.AppTextButton
import app.koyura.designsystem.components.UiTheme
import app.koyura.designsystem.theme.ThemePreset
import app.koyura.features.resources.Res
import app.koyura.features.resources.onboarding_back
import app.koyura.features.resources.onboarding_done
import app.koyura.features.resources.onboarding_guide_body
import app.koyura.features.resources.onboarding_guide_title
import app.koyura.features.resources.onboarding_next
import app.koyura.features.resources.onboarding_permission_body
import app.koyura.features.resources.onboarding_permission_granted
import app.koyura.features.resources.onboarding_permission_title
import app.koyura.features.resources.onboarding_skip
import app.koyura.features.resources.onboarding_storage_body
import app.koyura.features.resources.onboarding_storage_custom
import app.koyura.features.resources.onboarding_storage_default
import app.koyura.features.resources.onboarding_storage_title
import app.koyura.features.resources.onboarding_theme_body
import app.koyura.features.resources.onboarding_theme_title
import org.jetbrains.compose.resources.stringResource

const val OnboardingRootTag = "onboarding-root"
const val OnboardingProgressTag = "onboarding-progress"
const val OnboardingNextTag = "onboarding-next"
const val OnboardingBackTag = "onboarding-back"
const val OnboardingSkipTag = "onboarding-skip"
const val OnboardingStorageDefaultTag = "onboarding-storage-default"
const val OnboardingStorageCustomTag = "onboarding-storage-custom"
const val OnboardingPermissionTag = "onboarding-permission"

/** 四步引导（白皮书 07 §7.1）：主题 / 存储 / 权限 / 指南，完成后落盘 `onboardingCompleted`。 */
enum class OnboardingStep { THEME, STORAGE, PERMISSION, GUIDE }

/**
 * 首启引导。
 *
 * 平板没有截图（07:84 `⚠️缺口`），按手机单栏实现：内容限宽 560dp 居中，不做双栏。
 * 完成门槛：存储必须选中一项、权限必须确认；主题与指南可跳过。
 * 动画只用 `materialSharedAxisX` 等价的「位移 + 淡入」，不做装饰动效。
 */
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    onComplete: () -> Unit = {},
    onSkip: () -> Unit = {},
) {
    var stepIndex by remember { mutableIntStateOf(0) }
    var theme by remember { mutableStateOf(ThemePreset.DEFAULT) }
    var storageChosen by remember { mutableStateOf<String?>(null) }
    var permissionGranted by remember { mutableStateOf(false) }
    val steps = OnboardingStep.entries
    val step = steps[stepIndex]
    val canAdvance = when (step) {
        OnboardingStep.STORAGE -> storageChosen != null
        OnboardingStep.PERMISSION -> permissionGranted
        else -> true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(OnboardingRootTag)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DesignTokens.Spacing.Large, vertical = DesignTokens.Spacing.ExtraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(
            progress = { (stepIndex + 1f) / steps.size },
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .testTag(OnboardingProgressTag),
        )
        Spacer(Modifier.height(DesignTokens.Spacing.Large))
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            app.koyura.designsystem.ActionIcon(step.icon(), contentDescription = null)
        }
        Spacer(Modifier.height(DesignTokens.Spacing.Medium))
        AppText(
            text = stringResource(step.title()),
            style = UiTheme.typography.headlineLarge.copy(fontSize = 32.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(DesignTokens.Spacing.Small))
        AppText(
            text = stringResource(step.subtitle()),
            style = UiTheme.typography.bodyMedium,
            color = UiTheme.colorScheme.onSurfaceVariant.copy(alpha = DesignTokens.Alpha.Secondary),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        Spacer(Modifier.height(DesignTokens.Spacing.Large))
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .background(
                    UiTheme.colorScheme.surfaceContainer,
                    RoundedCornerShape(DesignTokens.Radius.Small),
                )
                .padding(DesignTokens.Spacing.Medium),
        ) {
            when (step) {
                OnboardingStep.THEME -> ThemeStep(theme) { theme = it }
                OnboardingStep.STORAGE -> StorageStep(storageChosen) { storageChosen = it }
                OnboardingStep.PERMISSION -> PermissionStep(permissionGranted) { permissionGranted = it }
                OnboardingStep.GUIDE -> GuideStep()
            }
        }
        Spacer(Modifier.height(DesignTokens.Spacing.Large))
        HorizontalDivider(thickness = DesignTokens.Spacing.Hairline)
        Spacer(Modifier.height(DesignTokens.Spacing.Medium))
        Row(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
        ) {
            AppTextButton(
                onClick = onSkip,
                modifier = Modifier.testTag(OnboardingSkipTag),
            ) { AppText(stringResource(Res.string.onboarding_skip)) }
            Spacer(Modifier.weight(1f))
            if (stepIndex > 0) {
                AppTextButton(
                    onClick = { stepIndex-- },
                    modifier = Modifier.testTag(OnboardingBackTag),
                ) { AppText(stringResource(Res.string.onboarding_back)) }
            }
            AppButton(
                onClick = {
                    if (stepIndex == steps.lastIndex) onComplete() else stepIndex++
                },
                enabled = canAdvance,
                modifier = Modifier.testTag(OnboardingNextTag),
            ) {
                AppText(
                    stringResource(
                        if (stepIndex == steps.lastIndex) Res.string.onboarding_done else Res.string.onboarding_next,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ThemeStep(selected: ThemePreset, onSelect: (ThemePreset) -> Unit) {
    listOf(
        ThemePreset.DEFAULT to "Default",
        ThemePreset.CATPPUCCIN to "Catppuccin",
        ThemePreset.GREEN_APPLE to "Green Apple",
        ThemePreset.MIDNIGHT_DUSK to "Midnight Dusk",
    ).forEach { (preset, label) ->
        StepOption(
            label = label,
            selected = preset == selected,
            onClick = { onSelect(preset) },
        )
    }
}

@Composable
private fun StorageStep(selected: String?, onSelect: (String) -> Unit) {
    val defaultLabel = stringResource(Res.string.onboarding_storage_default)
    val customLabel = stringResource(Res.string.onboarding_storage_custom)
    StepOption(
        label = defaultLabel,
        selected = selected == defaultLabel,
        onClick = { onSelect(defaultLabel) },
        modifier = Modifier.testTag(OnboardingStorageDefaultTag),
    )
    StepOption(
        label = customLabel,
        selected = selected == customLabel,
        onClick = { onSelect(customLabel) },
        modifier = Modifier.testTag(OnboardingStorageCustomTag),
    )
}

@Composable
private fun PermissionStep(granted: Boolean, onGranted: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        AppText(
            text = stringResource(Res.string.onboarding_permission_granted),
            modifier = Modifier.weight(1f),
            style = UiTheme.typography.bodyMedium,
        )
        AppSwitch(
            checked = granted,
            onCheckedChange = onGranted,
            modifier = Modifier.testTag(OnboardingPermissionTag),
        )
    }
}

@Composable
private fun GuideStep() {
    listOf(
        "书架：长按进入多选",
        "阅读器：点中央呼出工具栏",
        "更新：溢出菜单可调智能更新策略",
    ).forEach { line ->
        AppText(
            text = "· $line",
            modifier = Modifier.padding(vertical = DesignTokens.Spacing.ExtraSmall),
            style = UiTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun StepOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = DesignTokens.Spacing.Regular, horizontal = DesignTokens.Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = label,
            modifier = Modifier.weight(1f),
            color = if (selected) UiTheme.colorScheme.primary else UiTheme.colorScheme.onSurface,
            style = UiTheme.typography.bodyLarge,
        )
        if (selected) {
            app.koyura.designsystem.ActionIcon(ActionIcon.Checklist, contentDescription = null)
        }
    }
}

private fun OnboardingStep.title() = when (this) {
    OnboardingStep.THEME -> Res.string.onboarding_theme_title
    OnboardingStep.STORAGE -> Res.string.onboarding_storage_title
    OnboardingStep.PERMISSION -> Res.string.onboarding_permission_title
    OnboardingStep.GUIDE -> Res.string.onboarding_guide_title
}

private fun OnboardingStep.subtitle() = when (this) {
    OnboardingStep.THEME -> Res.string.onboarding_theme_body
    OnboardingStep.STORAGE -> Res.string.onboarding_storage_body
    OnboardingStep.PERMISSION -> Res.string.onboarding_permission_body
    OnboardingStep.GUIDE -> Res.string.onboarding_guide_body
}

private fun OnboardingStep.icon() = when (this) {
    OnboardingStep.THEME -> ActionIcon.Layout
    OnboardingStep.STORAGE -> ActionIcon.Storage
    OnboardingStep.PERMISSION -> ActionIcon.Info
    OnboardingStep.GUIDE -> ActionIcon.Help
}
