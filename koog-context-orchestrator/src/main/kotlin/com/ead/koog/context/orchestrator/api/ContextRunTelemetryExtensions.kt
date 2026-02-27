package com.ead.koog.context.orchestrator.api

import com.ead.koog.context.orchestrator.state.ContextSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Converts a context snapshot into a UI-friendly remaining-context percentage.
 */
fun ContextSnapshot.remainingPercentRounded(): Int =
    telemetry.remainingPercent.toInt().coerceIn(0, 100)

/**
 * Returns the latest known remaining-context percentage.
 */
fun ContextRunTelemetry.currentRemainingPercent(): Int =
    latest.remainingPercentRounded()

/**
 * Streams remaining-context percentage updates derived from live context snapshots.
 */
fun ContextRunTelemetry.remainingPercentFlow(): Flow<Int> =
    snapshots
        .map { snapshot -> snapshot.remainingPercentRounded() }
        .distinctUntilChanged()
