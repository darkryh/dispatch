# Dispatch Sample Application

This is a sample chat application built with the Dispatch terminal UI framework, demonstrating AI-powered conversations using Deepseek integration.

## Dispatch Framework Overview

Dispatch is a terminal UI library for Kotlin with a Jetpack Compose-inspired API. It provides:

- **Reactive UI**: Compose-style state management with automatic recomposition
- **Rich Widgets**: Text, Button, Input, Panel, LazyColumn, ProgressBar, and more
- **Navigation**: Type-safe routing between screens
- **ViewModel Architecture**: Scoped state management with coroutine support
- **Theming**: Built-in Dark, Light, and Minimal themes

## Entry Point

```kotlin
fun main(args: Array<String>) = DispatchApplication(args) {
    config {
        name = "my-app"
        version = "1.0.0"
        description = "My Terminal App"
        theme = DispatchTheme.Dark
        activeAreaHeight = 12

        // Define CLI arguments
        argument(name = "config", shortName = 'c', description = "Config file path")
        flag(name = "verbose", shortName = 'v', description = "Enable verbose output")
    }

    activeUI {
        // Your composable UI here
        MyApp()
    }
}
```

## Configuration Options

| Property | Type | Description |
|----------|------|-------------|
| `name` | String? | Application name |
| `version` | String? | Application version |
| `description` | String? | Application description |
| `theme` | DispatchTheme | UI theme (Dark, Light, Minimal) |
| `activeAreaHeight` | Int | Height of the active UI area (default: 12) |
| `mouseTracking` | MouseTracking | Mouse tracking mode |

## Available Widgets

### Text & Display
- `Text(text, style, maxLines, overflow)` - Styled text with markdown support
- `Panel(title, borderStyle) { content }` - Container with border
- `Background(color) { content }` - Background styling
- `Divider(style)` - Horizontal/vertical dividers
- `ProgressBar(progress, style, showPercentage)` - Progress indicator
- `Spinner(style, frame)` - Loading spinner

### Input & Buttons
- `TextField(value, onValueChange, placeholder)` - Text input
- `InputTextField(...)` - Text input with auto keyboard handling
- `PasswordField(...)` - Password input with masking
- `Button(onClick, style) { content }` - Clickable button
- `IconButton(icon, onClick)` - Icon-only button
- `ToggleButton(checked, onCheckedChange, style)` - Toggle switch
- `RadioButton(selected, onClick)` - Radio button
- `CommandPalette(options, inputValue, onOptionSelected)` - Command palette

### Lists & Scrolling
- `LazyColumn { items(...) }` - Lazy-loaded vertical list
- `ScrollableList(items, scrollState)` - Scrollable list container
- `Checklist(items, onCheckedChange)` - Multi-select checklist
- `TaskList(tasks)` - Task item list

### Layout
- `Column(arrangement, alignment) { content }` - Vertical stacking
- `Row(arrangement, alignment) { content }` - Horizontal stacking
- `Box(alignment) { content }` - Container layout

## State Management

### Basic State
```kotlin
var count by remember { mutableStateOf(0) }

Button(onClick = { count++ }) {
    Text("Count: $count")
}
```

### ViewModel
```kotlin
class MyViewModel : StateViewModel<MyState>(MyState()) {
    fun doSomething() {
        updateState { copy(loading = true) }
        viewModelScope.launch {
            // async work
            updateState { copy(loading = false) }
        }
    }
}

@Composable
fun MyScreen() {
    val viewModel = viewModel { MyViewModel() }
    val state by viewModel.state.collectAsState()

    if (state.loading) {
        Spinner()
    } else {
        Text(state.content)
    }
}
```

## Navigation

```kotlin
// Define routes
sealed class Routes {
    @Serializable object Home : Routes()
    @Serializable data class Detail(val id: String) : Routes()
}

@Composable
fun App() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = Routes.Home) {
        screen<Routes.Home> { HomeScreen(navController) }
        screen<Routes.Detail> { route -> DetailScreen(route.id) }
    }
}

// Navigate
navController.navigate(Routes.Detail(id = "123"))
navController.navigateUp()
```

## CompositionLocals

Access context values anywhere in the composition:

- `LocalTerminal` - Terminal instance
- `LocalTheme` - Current theme
- `LocalTerminalWidth` - Terminal width
- `LocalTerminalHeight` - Terminal height
- `LocalDispatchScope` - DispatchScope instance
- `LocalKeyboardInterceptor` - Keyboard event interceptor
- `LocalFocused` - Focus state
- `LocalEnabled` - Enabled state

## Sample App Structure

```
dispatch-sample/src/main/kotlin/com/ead/dispatch/sample/
├── Main.kt                    # Entry point
├── ai/
│   ├── prompts/               # AI prompt definitions
│   │   ├── Intent.kt          # Intent classification
│   │   ├── Planner.kt         # Action planning
│   │   ├── ShellCommander.kt  # Shell command generation
│   │   ├── Transformation.kt  # Data transformation
│   │   ├── Reviewer.kt        # Output review
│   │   └── ContextResover.kt  # Context resolution
│   └── util/
│       └── simpleDeepseekExecutor.kt  # Deepseek API integration
├── model/
│   ├── CliMessage.kt          # Message data class
│   └── CliMessageRole.kt      # Message role enum
└── ui/
    ├── DispatchSampleApp.kt   # Root navigation component
    ├── SampleRoutes.kt        # Route definitions
    ├── chat/
    │   ├── ChatScreen.kt      # Main chat interface
    │   ├── ChatViewModel.kt   # Chat state management
    │   ├── CommandRouting.kt  # Command parsing
    │   └── components/
    │       ├── Header.kt
    │       ├── StatusBar.kt
    │       └── ProccesingAnimation.kt
    ├── commands/
    │   └── SampleCommands.kt  # Command definitions
    └── help/
        └── HelpScreen.kt      # Help screen
```

## Module Structure

The Dispatch framework is organized into these modules:

| Module | Purpose |
|--------|---------|
| `dispatch-core` | DispatchApplication entry point and CLI lifecycle |
| `dispatch-runtime` | Composition system, state management, CompositionLocals |
| `dispatch-layout` | Layout components (Column, Row, Box) |
| `dispatch-lifecycle` | Lifecycle management |
| `dispatch-navigation` | Navigation routing (NavHost, NavController) |
| `dispatch-renderer` | Terminal rendering (FrameBuffer, RenderLoop) |
| `dispatch-viewmodel` | ViewModel architecture |
| `dispatch-widgets` | UI widgets (Button, Text, Input, Panel, etc.) |

## Themes

Three built-in themes are available:

- `DispatchTheme.Dark` - Dark color scheme (default)
- `DispatchTheme.Light` - Light color scheme
- `DispatchTheme.Minimal` - Minimal styling

Each theme provides colors for: primary, secondary, muted, accent, success, warning, error, info, code, link, border, cursor, selection, userMessage, assistantMessage, systemMessage.
