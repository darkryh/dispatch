package com.ead.dispatch.sample.presentation.session

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
import com.ead.dispatch.navigation.NavBackStack
import com.ead.dispatch.navigation.NavKey
import com.ead.dispatch.navigation.navigate
import com.ead.dispatch.navigation.popBackStack
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.sample.navigation.ChatRoute
import com.ead.dispatch.viewmodel.collectAsState
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.LazyColumn
import com.ead.dispatch.widget.SessionDisplayColumn
import com.ead.dispatch.widget.SessionOption
import com.ead.dispatch.widget.SessionSelector
import com.ead.dispatch.widget.SessionSelectorTextStyles
import com.ead.dispatch.widget.Text
import com.github.ajalt.mordant.rendering.TextStyle

@Dispatchable
fun SessionScreen(
    backStack: NavBackStack<NavKey>
) {
    val viewModel = viewModel<SessionViewModel>()
    val theme = LocalTheme.current

    val sessions = viewModel.sessions.collectAsState()
    val isLoading = viewModel.isLoading.collectAsState()

    // Convert sessions to SessionOptions
    val sessionOptions = sessions.value.map { session ->
        SessionOption(
            id = session.id,
            title = session.title,
            updatedTime = viewModel.formatRelativeTime(session.updatedAt),
            conversationId = session.id.take(12) + "...",  // Truncate for display
            messageCount = session.messageCount,
            data = session,
        )
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        // Header
        item { Spacer(Modifier.height(1)) }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(2))
                Text(
                    text = "Resume Session",
                    style = theme.primary + TextStyle(bold = true),
                )
            }
        }
        item { Spacer(Modifier.height(1)) }

        // Subtitle
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(2))
                Text(
                    text = "Select a previous conversation to continue:",
                    style = theme.muted,
                )
            }
        }
        item { Spacer(Modifier.height(1)) }

        // Loading state
        if (isLoading.value) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.width(2))
                    Text(
                        text = "Loading sessions...",
                        style = theme.muted,
                    )
                }
            }
        } else {
            // Session Selector
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.width(2))

                        SessionSelector(
                            options = sessionOptions,
                            onOptionSelected = { option ->
                                viewModel.selectSession(option.data)
                                backStack.navigate(ChatRoute(conversationId = option.data.id))
                            },
                        onExit = {
                            backStack.popBackStack()
                        },
                        columns = listOf(
                            SessionDisplayColumn.UPDATED_TIME,
                            SessionDisplayColumn.CONVERSATION_ID,
                            SessionDisplayColumn.TITLE,
                        ),
                        textStyles = SessionSelectorTextStyles(
                            prefix = theme.muted,
                            selectedPrefix = theme.accent + TextStyle(bold = true),
                            updatedTime = theme.muted,
                            selectedUpdatedTime = theme.accent,
                            conversationId = theme.muted,
                            selectedConversationId = theme.accent,
                            title = theme.muted,
                            selectedTitle = theme.accent + TextStyle(bold = true),
                            messageCount = theme.muted,
                            selectedMessageCount = theme.accent,
                            noResultsText = theme.muted,
                            header = theme.primary + TextStyle(bold = true),
                        )
                    )
                }
            }
        }

        item { Spacer(Modifier.height(1)) }

        // Footer with instructions
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(2))
                Text(
                    text = "Arrow keys to navigate, Enter to select, Esc to cancel, Type to search",
                    style = theme.muted,
                )
            }
        }
    }
}
