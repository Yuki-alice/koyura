package app.koyura.features.updates

import app.koyura.domain.updates.SmartUpdatePreferences

/**
 * 智能更新策略（界面侧契约）：与引擎偏好模型 `SmartUpdatePreferences` 字段逐字对齐，
 * 字段语义即 `LibraryUpdateScanner.skipReason` 的跳过规则。
 *
 * 引擎侧已交付偏好读写（`preferences()/setPreferences()`），本轮只补界面契约与对话框；
 * 生产桥接（把本仓储接到 scanner）由主 agent 晨间完成，见交付报告。
 */
data class UpdatePolicy(
    /** 智能跳过总开关；关闭后扫描不再跳过任何作品。 */
    val enabled: Boolean = true,
    /** 跳过已完结作品。 */
    val skipCompleted: Boolean = true,
    /** 跳过还有未读章节的作品（优先补读而不是追新）。 */
    val skipWithUnread: Boolean = true,
    /** 跳过从未开始阅读的作品。 */
    val skipNotStarted: Boolean = true,
    /** 跳过下次更新日在今天之后（未到发布窗口）的作品。 */
    val skipOutsideReleasePeriod: Boolean = true,
    /** 标题包含任一排除词即跳过。 */
    val excludedTitleTerms: List<String> = emptyList(),
) {
    /** 归一化排除词：去首尾空白、丢空项、按小写去重（与引擎写入同一口径）。 */
    fun normalized(): UpdatePolicy = copy(
        excludedTitleTerms = excludedTitleTerms
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() },
    )
}

/** 界面契约 -> 引擎偏好模型。 */
internal fun UpdatePolicy.toPreferences(): SmartUpdatePreferences = SmartUpdatePreferences(
    enabled = enabled,
    skipCompleted = skipCompleted,
    skipWithUnread = skipWithUnread,
    skipNotStarted = skipNotStarted,
    skipOutsideReleasePeriod = skipOutsideReleasePeriod,
    excludedTitleTerms = normalized().excludedTitleTerms,
)

/** 引擎偏好模型 -> 界面契约。 */
internal fun SmartUpdatePreferences.toUpdatePolicy(): UpdatePolicy = UpdatePolicy(
    enabled = enabled,
    skipCompleted = skipCompleted,
    skipWithUnread = skipWithUnread,
    skipNotStarted = skipNotStarted,
    skipOutsideReleasePeriod = skipOutsideReleasePeriod,
    excludedTitleTerms = excludedTitleTerms,
)

/** 策略仓储：读当前策略、整份覆盖写入。生产实现桥到 `LibraryUpdateScanner`。 */
interface UpdatePolicyRepository {
    suspend fun policy(): UpdatePolicy
    suspend fun apply(policy: UpdatePolicy)
}

/** 策略读取态（对齐 Updates 三态）。 */
sealed interface UpdatePolicyLoadState {
    data object Loading : UpdatePolicyLoadState
    data object Ready : UpdatePolicyLoadState
    data class Failed(val message: String) : UpdatePolicyLoadState
}
