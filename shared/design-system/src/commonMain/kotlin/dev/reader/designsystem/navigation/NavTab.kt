package dev.reader.designsystem.navigation

/**
 * The five top-level destinations shared by the compact bottom bar and the expanded navigation rail.
 *
 * Declaration order is the canonical display order: Library, Updates, History, Browse, More.
 */
enum class NavTab {
    Library,
    Updates,
    History,
    Browse,
    More,
}

/** Controls whether navigation destinations always render their text label. */
enum class NavLabelMode {
    /** Every destination shows its label, matching the default Material 3 navigation layout. */
    AlwaysShow,

    /** Only the selected destination shows its label; the others rely on icon and accessibility name. */
    SelectedOnly,
}
