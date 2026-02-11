# dispatch-reliability

Reliability-focused test module for Dispatch.

This module is intended to stress the UI runtime with deterministic scenario tests across different
screen archetypes and interaction patterns.

Current suites:

- `ModeCyclingReliabilityTest`: long-history mode switching stability checks.
- `DictationBurstReliabilityTest`: dictation-like burst updates and footer persistence.
- `NavigationChurnReliabilityTest`: rapid cross-screen navigation with mixed layouts.
- `ReliabilityDesignMatrixTest`: multi-archetype UI matrix across terminal sizes/themes, with bounded/unbounded layout passes.
- `NavigationStackReliabilityTest`: real `NavBackStack` + `NavDisplay` churn with route lifecycle transitions.
- `ReliabilityFuzzSystemTest`: seeded randomized interaction stress test with route/mode/input mutations.

Run:

```bash
./gradlew :dispatch-reliability:test
```
