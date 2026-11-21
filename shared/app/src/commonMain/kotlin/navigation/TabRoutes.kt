package app.koyura.app.navigation

import app.koyura.designsystem.navigation.NavTab
import kotlinx.serialization.Serializable

/** Root destination of one top-level tab. */
@Serializable
data class TabRootRoute(val tab: NavTab) : TabKey
