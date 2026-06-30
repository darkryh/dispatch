package com.ead.dispatch.sample.navigation

import com.ead.dispatch.navigation.NavKey

/**
 * Single source of truth for every category screen in the sample.
 *
 * Both the Home launcher grid ([com.ead.dispatch.sample.designsystem.LauncherGrid]) and the global
 * "go to…" palette ([com.ead.dispatch.sample.designsystem.GlobalCommandPalette]) are generated from
 * this list, so a new category screen only needs an entry here (plus its route, screen and
 * view-model) to become reachable everywhere.
 *
 * Cards are laid out in [COLUMNS] columns in enum order; the Home view-model turns 2-D arrow
 * movement into the flat [ordinal] cursor.
 */
enum class CatalogDestination(
    val title: String,
    val glyph: String,
    val blurb: String,
    val route: NavKey,
) {
    INPUTS("Inputs", "▦", "Text & password fields", InputsRoute),
    BUTTONS("Buttons & Selection", "●", "Buttons & toggle sets", ButtonsRoute),
    LISTS("Lists", "±", "Scrollable item lists", ListsRoute),
    TABLES("Tables & Grid", "▦", "Grids & data tables", TablesRoute),
    HIERARCHY("Hierarchy & Command", "⌥", "Trees & command menus", HierarchyRoute),
    TASKS("Checklist & Tasks", "✔", "Checklists & tasks", TasksRoute),
    PROGRESS("Progress", "◴", "Progress & spinners", ProgressRoute),
    SURFACES("Surfaces & Dividers", "▭", "Panels, cards & rules", SurfacesRoute),
    LAYOUT("Layout", "▤", "Boxes, rows & flows", LayoutRoute),
    REVIEW("Diff & Review", "±", "File diffs & review", ReviewRoute),
    CHAT("Chat", "💬", "Live streaming chat", ChatRoute()),
    ;

    companion object {
        /** Number of columns the centred launcher grid lays cards out in. */
        const val COLUMNS: Int = 3
    }
}
