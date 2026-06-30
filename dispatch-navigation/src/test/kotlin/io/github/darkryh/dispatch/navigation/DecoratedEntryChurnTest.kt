package io.github.darkryh.dispatch.navigation

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertSame

@Serializable
private data class ChurnRoute(
    val id: Int,
) : NavKey

/**
 * Guards TIER 2 #7: decorated entries must not churn (be rebuilt) on every
 * recomposition when the back stack is unchanged. Uses the 320-step churn style
 * from NavigationStackReliabilityTest.
 */
class DecoratedEntryChurnTest {
    @Test
    fun `decorated entries keep identity across recompositions when back stack unchanged`() {
        val backStack = NavBackStack<ChurnRoute>(ChurnRoute(0))
        var captured: List<NavBackStackEntry<ChurnRoute>>? = null

        val content: @Composable () -> Unit = {
            captured =
                rememberDecoratedNavEntries(
                    backStack = backStack,
                    entryProvider = { key -> NavEntry(key = key) {} },
                )
        }

        NavCompositionHarness().use { harness ->
            repeat(320) { step ->
                when (step % 5) {
                    0 -> backStack.navigate(ChurnRoute(step))
                    1 -> backStack.navigate(ChurnRoute(step + 1000))
                    2 -> backStack.navigate(ChurnRoute(step + 2000))
                    else -> backStack.popBackStack()
                }

                // First render after a mutation.
                harness.render(content)
                val afterMutation = requireNotNull(captured)

                // Second render WITHOUT any mutation: every decorated entry must
                // be the SAME instance (no per-frame rebuild / fold churn).
                harness.render(content)
                val afterStableFrame = captured

                afterMutation.indices.forEach { i ->
                    assertSame(
                        afterMutation[i],
                        requireNotNull(afterStableFrame?.get(i)),
                        "decorated entry $i churned on unchanged recomposition at step=$step",
                    )
                }
            }
        }
    }
}
