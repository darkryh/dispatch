@file:Suppress("unused")

package com.ead.dispatch.ui

/**
 * Dispatch UI Facade - single import for common UI components.
 *
 * Usage:
 * ```kotlin
 * import com.ead.dispatch.ui.*
 * ```
 *
 * This provides access to all common UI components, layouts, modifiers,
 * state management, and utilities without needing multiple imports.
 */

// ═══════════════════════════════════════════════════════════════════════════════
// Annotations
// ═══════════════════════════════════════════════════════════════════════════════

typealias Dispatchable = com.ead.dispatch.annotation.Dispatchable

// ═══════════════════════════════════════════════════════════════════════════════
// Layout Components
// ═══════════════════════════════════════════════════════════════════════════════

// Column, Row, Box are functions - import via extension
// Use: import com.ead.dispatch.layout.Column
// Use: import com.ead.dispatch.layout.Row
// Use: import com.ead.dispatch.layout.Box

// ═══════════════════════════════════════════════════════════════════════════════
// Modifier
// ═══════════════════════════════════════════════════════════════════════════════

typealias Modifier = com.ead.dispatch.modifier.Modifier

// ═══════════════════════════════════════════════════════════════════════════════
// State Management
// ═══════════════════════════════════════════════════════════════════════════════

typealias State<T> = com.ead.dispatch.state.State<T>
typealias MutableState<T> = com.ead.dispatch.state.MutableState<T>

// ═══════════════════════════════════════════════════════════════════════════════
// CompositionLocals
// ═══════════════════════════════════════════════════════════════════════════════

typealias CompositionLocal<T> = com.ead.dispatch.runtime.CompositionLocal<T>
