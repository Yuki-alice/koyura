package app.koyura.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 导航路由根（G3：按层归属拆分的前置）。
 *
 * 层级 = 推送目标层，与 [AppNavigationState] 的三层栈一一对应：
 * - [TabKey] → 5 个 tab 根栈（只放根）；
 * - [SharedKey] → 跨 tab 共享栈（详情/搜索/设置/下载队列/分类/统计/工具/引导…）；
 * - [ReaderKey] → 独立阅读器全屏栈（CLEAR_TOP）。
 *
 * `push` 按静态类型路由：[SharedKey] 进共享栈、[ReaderKey] 走阅读器层，
 * 传错层在编译期暴露。序列化注册见 [RouteRegistry]（显式逐条 iOS 安全不用反射）。
 */
@Serializable
sealed interface AppKey : NavKey

/** Tab 根路由键。 */
@Serializable
sealed interface TabKey : AppKey

/** 跨 tab 共享二级页路由键。 */
@Serializable
sealed interface SharedKey : AppKey

/** 独立阅读器层路由键。 */
@Serializable
sealed interface ReaderKey : AppKey
