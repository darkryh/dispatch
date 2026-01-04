package com.ead.dispatch.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Creates a new CoroutineScope for Dispatch operations.
 *
 * Uses [Dispatchers.Default] with a [SupervisorJob] for isolation.
 * Each scope is independent - failures in one coroutine don't cancel siblings.
 *
 * @return A new [CoroutineScope] suitable for Dispatch background operations.
 */
fun dispatchCoroutineScope(): CoroutineScope =
    CoroutineScope(Dispatchers.Default + SupervisorJob())
