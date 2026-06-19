# Dispatch UI sample

This module is an offline showcase for Dispatch navigation, widgets, Koin view models, and reactive
streaming updates. It does not call a network service or an AI framework.

The widget catalogue is split into Controls, Surfaces, Data, Progress, Lists, Review, and Workflow
screens. A source-coverage test requires every public widget family to remain represented in the
sample.

## Test layers

Fast tests validate the response simulator and view-model state transitions without launching a
process:

```bash
./gradlew :dispatch-sample:test
```

Terminal end-to-end tests build the installed distribution, launch it through an operating-system
pseudo-terminal, send raw key input, and assert the normalized ANSI transcript:

```bash
./gradlew :dispatch-sample:terminalE2eTest
```

The PTY suite covers:

- typed navigation between the home, chat, and component screens;
- rapid input through the real terminal decoder;
- randomized delayed streaming and final completion;
- cancellation with Escape while a stream is active;
- raw and masked text editing through the installed application;
- focus traversal and the ANSI focus background emitted by buttons;
- button activation and reactive state feedback;
- source coverage for every public widget family.

The task runs automatically as part of `:dispatch-sample:check`. PTY scenarios are serialized,
bounded by explicit timeouts, and always terminate their child process. They run on macOS and Linux
when `/usr/bin/script` is available; JUnit reports them as skipped on unsupported operating systems.

Normal sample runs use random chunk timing. The E2E process injects a fixed seed and bounded delays
through `DISPATCH_SAMPLE_STREAM_SEED`, `DISPATCH_SAMPLE_STREAM_MIN_DELAY_MS`, and
`DISPATCH_SAMPLE_STREAM_MAX_DELAY_MS` so failures are reproducible.
