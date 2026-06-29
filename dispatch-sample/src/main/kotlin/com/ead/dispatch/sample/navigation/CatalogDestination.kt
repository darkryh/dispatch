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
    INPUTS("Inputs", "⌨", "Text & password", InputsRoute),
    BUTTONS("Buttons & Selection", "⏺", "Buttons & toggles", ButtonsRoute),
    LISTS("Lists", "☰", "Scrollable lists", ListsRoute),
    TABLES("Tables & Grid", "▦", "Grid & tables", TablesRoute),
    HIERARCHY("Hierarchy & Command", "⌥", "Tree & commands", HierarchyRoute),
    TASKS("Checklist & Tasks", "✔", "Checklists & tasks", TasksRoute),
    PROGRESS("Progress", "◴", "Bars & spinners", ProgressRoute),
    SURFACES("Surfaces & Dividers", "▭", "Panels & dividers", SurfacesRoute),
    LAYOUT("Layout", "▤", "Boxes & flows", LayoutRoute),
    REVIEW("Diff & Review", "±", "File diffs", ReviewRoute),
    CHAT("Chat", "💬", "Streaming chat", ChatRoute()),
    ;

    companion object {
        /** Number of columns the centred launcher grid lays cards out in. */
        const val COLUMNS: Int = 3
    }
}
