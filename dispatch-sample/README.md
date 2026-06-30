# Dispatch Sample

An interactive, offline showcase of the **Dispatch** terminal-UI framework. Every screen is a
*playground*: you don't just look at a widget, you drive it — cycle its styles, toggle its flags,
type into it — so you can feel exactly what each capability does in a real terminal.

It is also a reference implementation of the architecture Dispatch encourages: **MVI** view-models
built on the library's own base classes, typed **navigation**, **Koin** DI, and a small reusable
"design-system" layer you can copy into your own app.

## Run it

From the repository root:

```bash
./build-execute.sh
```

Requires JDK 21+ and a real ANSI terminal (a true TTY — not the Gradle console). That is exactly why
this uses `build-execute.sh` rather than `./gradlew :dispatch-sample:run`: the Gradle `run` task
gives the app no TTY (stdin/stdout are captured), so the TUI gets no input and the rendering garbles.
`build-execute.sh` installs a native launcher with `installDist` and execs it directly. For a
deterministic chat stream while exploring, set a seed:

```bash
DISPATCH_SAMPLE_STREAM_SEED=1 ./build-execute.sh
```

## Getting around

The app opens on a **launcher grid**. Move the highlight with the arrow keys and press Enter to open
a category. From anywhere:

| Key | Action |
|---|---|
| `↑ ↓ ← →` | Move the launcher cursor (Home) / move & change controls (playgrounds) |
| `Enter` | Open the selected category / activate a focused widget |
| `Space` | Flip the selected toggle control |
| `Tab` | Move focus between interactive widgets (text fields, menus, trees) |
| `Ctrl+P` | Open the global "go to…" palette — jump to any screen |
| `Esc` | Back to the previous screen (and, in Chat, stop a running stream) |
| `Ctrl+C` ×2 | Quit |

Inside a playground the uniform contract is: **↑/↓** moves the selected control row, **←/→** changes
a cycle (e.g. a style), **Space** flips a toggle, and the **Preview** pane re-renders live.

## Screens → widgets

| Screen | Widgets demonstrated |
|---|---|
| **Inputs** | `TextField` (value + `TextFieldState`), `BasicTextFieldRenderer`, `PasswordField`, input history, masking |
| **Buttons & Selection** | `Button` ×5 `ButtonStyle`, `IconButton`, `ButtonRow`, `ToggleButton` ×5 `ToggleStyle`, `RadioButton`, `SegmentedButton`, `Modifier.clickable` |
| **Lists** | `SelectableList`, `SelectableWindowedList`, `ScrollableListWithIndicator`, `LazyColumn`, `SelectMenu`, `MultiSelectList`, `ScrollState` |
| **Tables & Grid** | `Grid` (`GridCells.Fixed`/`Adaptive`), `Table` (`TableColumnWidth.Auto/Fixed/Weight`), `FilterableTable` |
| **Hierarchy & Command** | `Tree` (expand/collapse), `DecisionPrompt`, `CommandPalette` |
| **Checklist & Tasks** | `Checklist` ×4 `ChecklistStyle`, `TaskList` (all 5 `TaskStatus`), `CountTileGrid` |
| **Progress** | `LinearProgressIndicator` ×6 styles, `Spinner` ×6 styles, `LoadingIndicator`, `TransferProgress` — animated by the view-model |
| **Surfaces & Dividers** | `Text` (`align`/`maxLines`/`overflow`/markdown), `Panel` (all `BorderStyle`), `Surface`, `HorizontalDivider`/`VerticalDivider` (all `DividerStyle`), `Chip`/`ChipRow` |
| **Layout** | `Box`, `Row`/`Column` arrangements & alignments, `FlowRow`, a custom `Layout` + `MeasurePolicy`, `HeaderLayout`, `Modifier.padding/border/weight/verticalScroll` |
| **Diff & Review** | `FileDiff` (all `DiffFocus`, context lines, stats), `DiffReviewPanel` |
| **Chat** | streaming `TextField` composer, slash `CommandPalette`, input history, Esc-to-cancel |

The test `WidgetCatalogCoverageTest` statically asserts that *every* public widget family appears on
one of these screens, so the catalog can't silently drift.

## Architecture

```
presentation/<feature>/   XScreen.kt        @Composable, renders state + sends intents
                          XViewModel.kt     MVI view-model (StateFlow<State> + handleIntent)
designsystem/             AppScaffold            header + body + footer + Esc-back
                          GlobalCommandPalette   Ctrl+P "go to…" overlay
                          LauncherGrid           Home's 2-D card grid
                          PlaygroundScaffold     preview pane + controls pane
                          PlaygroundControls     ControlSpec + ControlPanel + PlaygroundController
navigation/               SampleRoutes.kt        @Serializable NavKey routes
                          CatalogDestination.kt  single source of truth for screens
domain/chat/              ChatRepository, ResponseSimulator (unchanged, fully offline)
di/SampleModule.kt        Koin: domain singletons + one view-model per screen
```

**MVI base classes used** (from `dispatch-viewmodel`):

| Base class | Used by |
|---|---|
| `MviViewModel<S, I>` | Home, Buttons, Lists, Tables, Tasks, Progress, Surfaces, Layout, Review, Inputs, Hierarchy |
| `FullMviViewModel<S, I, E>` | Chat (adds a `ScrollToBottom` side-effect) |

Most playgrounds share one `PlaygroundIntent` (`Select` / `Change` / `Toggle`), so wiring a new
screen is: define a `State`, map the three intents, and describe the controls. Screens whose widgets
own the keyboard (Inputs, Hierarchy) define their own intents instead.

### Keyboard priority ladder

Key handling is layered through `LocalKeyboardInterceptor` (see `AppKeyPriority`); higher numbers see
a key first and may consume it:

| Priority | Owner |
|---|---|
| 1000 | Global "go to…" palette (Ctrl+P) — modal while open |
| 900 | Home launcher-grid 2-D navigation |
| 100 | Chat Esc-to-cancel (only while streaming) |
| 60 | Playground controls (↑↓ / ←→ / Space) |
| 5 | Esc-as-back (`AppScaffold`) |
| -1 | Library focusable widgets (only while focused) |

## Environment knobs

The chat's `ResponseSimulator` is configurable (no network, no AI):

| Variable | Default | Meaning |
|---|---|---|
| `DISPATCH_SAMPLE_STREAM_SEED` | random | Seed for reproducible chunking/wording |
| `DISPATCH_SAMPLE_STREAM_MIN_DELAY_MS` | 35 | Minimum inter-chunk delay |
| `DISPATCH_SAMPLE_STREAM_MAX_DELAY_MS` | 140 | Maximum inter-chunk delay |

## Tests

```bash
./gradlew :dispatch-sample:test              # unit + render + coverage (fast, no process launch)
./gradlew :dispatch-sample:terminalE2eTest   # PTY end-to-end suite (builds installDist, drives a real TTY)
```

- `HomeViewModelTest` — pure 2-D launcher-grid arithmetic.
- `ChatViewModelTest` — streaming, cancellation, and cross-view-model persistence via the MVI API.
- `WidgetCatalogCoverageTest` — every public widget family is demonstrated.
- `widgets/*Test` — render tests for the app-level composite widgets.
- `e2e/*` — PTY-driven end-to-end tests (tagged `terminal-e2e` / `terminal-stress`), also run by
  `:dispatch-sample:check`. They run on macOS/Linux where `/usr/bin/script` is available and are
  reported as skipped on unsupported systems.
