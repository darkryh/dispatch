package io.github.darkryh.dispatch.sample.presentation.lists

import io.github.darkryh.dispatch.viewmodel.MviViewModel

/** Short demo entries shared by every list widget in the preview pane (kept small so it stays compact). */
val SAMPLE_ITEMS = listOf("Inbox", "Drafts", "Sent", "Archive", "Spam")

/**
 * State of the Lists playground — just a live echo of what the two interactive, Tab-focusable
 * widgets emit, shown in the controls pane.
 *
 * @param menuChoice the last [io.github.darkryh.dispatch.widget.SelectMenu] item activated with Enter.
 * @param checkedCount how many rows are currently checked in the [io.github.darkryh.dispatch.widget.MultiSelectList].
 */
data class ListsState(
    val menuChoice: String = "—",
    val checkedCount: Int = 0,
)

/** Things the focusable list widgets report back to the view-model. */
sealed interface ListsIntent {
    data class MenuPicked(
        val label: String,
    ) : ListsIntent

    data class MultiChanged(
        val count: Int,
    ) : ListsIntent
}

/**
 * Drives the Lists playground. Unlike the style-cycling screens, the list widgets here own the
 * keyboard themselves (Tab to focus, then ↑/↓/Space/Enter), so this view-model has no
 * `PlaygroundController`; it simply records what the focused widgets emit for the controls echo.
 */
class ListsViewModel : MviViewModel<ListsState, ListsIntent>(ListsState()) {
    override suspend fun handleIntent(intent: ListsIntent) {
        when (intent) {
            is ListsIntent.MenuPicked -> updateState { it.copy(menuChoice = intent.label) }
            is ListsIntent.MultiChanged -> updateState { it.copy(checkedCount = intent.count) }
        }
    }
}
