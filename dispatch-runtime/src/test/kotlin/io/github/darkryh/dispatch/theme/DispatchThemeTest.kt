package io.github.darkryh.dispatch.theme

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression tests for the theme string-convenience methods.
 *
 * These were previously implemented as `fun primary(text: String) = primary(text)` — Kotlin
 * resolves the bare call to the member function itself (not the same-named TextStyle property's
 * invoke operator), producing infinite recursion and a StackOverflowError on every call.
 */
class DispatchThemeTest {
    @Test
    fun `string convenience methods do not recurse and preserve the text`() {
        val themes = listOf(DispatchTheme.Dark, DispatchTheme.Light, DispatchTheme.Minimal)
        for (theme in themes) {
            val applications =
                mapOf(
                    "primary" to theme.primary("sample"),
                    "secondary" to theme.secondary("sample"),
                    "muted" to theme.muted("sample"),
                    "accent" to theme.accent("sample"),
                    "success" to theme.success("sample"),
                    "warning" to theme.warning("sample"),
                    "error" to theme.error("sample"),
                    "info" to theme.info("sample"),
                )
            applications.forEach { (name, styled) ->
                assertTrue(styled.contains("sample"), "$name(text) should contain the input text")
            }
        }
    }

    @Test
    fun `string convenience methods match the style property's own rendering`() {
        val theme = DispatchTheme.Dark
        assertTrue(theme.primary("x") == theme.primary.invoke("x"))
        assertTrue(theme.error("x") == theme.error.invoke("x"))
    }
}
