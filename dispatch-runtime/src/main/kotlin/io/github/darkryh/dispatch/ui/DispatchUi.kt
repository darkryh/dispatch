@file:Suppress("unused")

package io.github.darkryh.dispatch.ui

/**
 * Dispatch UI Facade - single import for common UI components.
 *
 * Usage:
 * ```kotlin
 * import io.github.darkryh.dispatch.ui.*
 * ```
 *
 * This provides access to all common UI components, layouts, modifiers,
 * state management, and utilities without needing multiple imports.
 */

// ═══════════════════════════════════════════════════════════════════════════════
// Layout Components
// ═══════════════════════════════════════════════════════════════════════════════

// Column, Row, Box are functions - import via extension
// Use: import io.github.darkryh.dispatch.layout.Column
// Use: import io.github.darkryh.dispatch.layout.Row
// Use: import io.github.darkryh.dispatch.layout.Box

// ═══════════════════════════════════════════════════════════════════════════════
// Modifier
// ═══════════════════════════════════════════════════════════════════════════════

typealias Modifier = io.github.darkryh.dispatch.modifier.Modifier
