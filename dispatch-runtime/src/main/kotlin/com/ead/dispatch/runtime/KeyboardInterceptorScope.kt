package com.ead.dispatch.runtime

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.state.remember

/**
 * Provide a scoped KeyboardInterceptor that falls back to the parent interceptor.
 *
 * Useful for screens or overlays that want isolated key handling while still
 * allowing higher-level interceptors to run when not consumed locally.
 */
@Dispatchable
fun KeyboardInterceptorScope(content: @Dispatchable () -> Unit) {
    val parent = LocalKeyboardInterceptor.current
    val scoped = remember(parent) { KeyboardInterceptor(parent) }
    CompositionLocalProvider(LocalKeyboardInterceptor provides scoped) {
        content()
    }
}
