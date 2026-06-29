package io.github.darkryh.dispatch.initializr.template

/**
 * The "minimal runnable app" starter template: a complete, idiomatic Dispatch project expressed as
 * placeholdered files. The generator ([io.github.darkryh.dispatch.initializr.generate.ProjectGenerator])
 * substitutes the `{{...}}` tokens with the user's [io.github.darkryh.dispatch.initializr.model.ProjectConfig].
 *
 * What it produces is a single-screen Dispatch app wired with Koin DI and the library's MVI base
 * class ([com.ead.dispatch.viewmodel.MviViewModel]) — the blessed pattern, kept as small as possible:
 * `DispatchApplication { config { koin { … } }; content { … } }`, one `AppModule`, one screen, one
 * view-model with a tiny counter intent loop, all driven by the keyboard.
 *
 * ### Placeholders
 * `{{PROJECT_NAME}}` `{{GROUP_ID}}` `{{ARTIFACT_ID}}` `{{PACKAGE}}` `{{PACKAGE_PATH}}`
 * `{{APP_VERSION}}` `{{DISPATCH_VERSION}}` `{{KOTLIN_VERSION}}` `{{GRADLE_VERSION}}` `{{JVM}}`
 * and `{{D}}` — a literal `$` (so Kotlin string templates in the generated sources stay readable here).
 */
object StarterTemplate {
    /**
     * The Dispatch artifact version the generated project depends on. The modules are published to
     * Maven Central under `io.github.darkryh.dispatch:*`; flipping this one constant is all that is
     * needed when a new release lands. NOTE: the generated project only resolves once this version is
     * actually published (the library is currently on a `-SNAPSHOT`).
     */
    const val DISPATCH_VERSION: String = "1.0.0"

    /** Kotlin version for the generated project — kept in lockstep with the library's own. */
    const val KOTLIN_VERSION: String = "2.4.0"

    /** Gradle version the generated wrapper targets. */
    const val GRADLE_VERSION: String = "9.6.1"

    /** JVM toolchain the generated project compiles against. */
    const val JVM_TARGET: String = "21"

    /** The raw, placeholdered files. Paths are repository-relative; `{{PACKAGE_PATH}}` expands to dirs. */
    fun files(): List<TemplateFile> =
        listOf(
            TemplateFile("settings.gradle.kts", SETTINGS_GRADLE),
            TemplateFile("gradle.properties", GRADLE_PROPERTIES),
            TemplateFile("build.gradle.kts", BUILD_GRADLE),
            TemplateFile(".gitignore", GITIGNORE),
            TemplateFile("README.md", README),
            TemplateFile("src/main/kotlin/{{PACKAGE_PATH}}/Main.kt", MAIN_KT),
            TemplateFile("src/main/kotlin/{{PACKAGE_PATH}}/MainScreen.kt", MAIN_SCREEN_KT),
            TemplateFile("src/main/kotlin/{{PACKAGE_PATH}}/MainViewModel.kt", MAIN_VIEW_MODEL_KT),
            TemplateFile("src/main/kotlin/{{PACKAGE_PATH}}/di/AppModule.kt", APP_MODULE_KT),
        )

    private val SETTINGS_GRADLE =
        """
        pluginManagement {
            repositories {
                google()
                mavenCentral()
                gradlePluginPortal()
            }
        }

        rootProject.name = "{{ARTIFACT_ID}}"
        """.trimIndent() + "\n"

    private val GRADLE_PROPERTIES =
        """
        kotlin.code.style=official
        org.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8
        org.gradle.caching=true
        """.trimIndent() + "\n"

    private val BUILD_GRADLE =
        """
        plugins {
            kotlin("jvm") version "{{KOTLIN_VERSION}}"
            // Dispatch composables are compiled by the Compose compiler, shipped with Kotlin.
            id("org.jetbrains.kotlin.plugin.compose") version "{{KOTLIN_VERSION}}"
            application
        }

        group = "{{GROUP_ID}}"
        version = "{{APP_VERSION}}"

        repositories {
            google()
            mavenCentral()
        }

        dependencies {
            // `dispatch-widgets` transitively brings runtime, layout, viewmodel, the Compose runtime,
            // coroutines and Mordant; the others are listed explicitly for clarity.
            implementation("io.github.darkryh.dispatch:dispatch-runtime:{{DISPATCH_VERSION}}")
            implementation("io.github.darkryh.dispatch:dispatch-layout:{{DISPATCH_VERSION}}")
            implementation("io.github.darkryh.dispatch:dispatch-widgets:{{DISPATCH_VERSION}}")
            implementation("io.github.darkryh.dispatch:dispatch-viewmodel:{{DISPATCH_VERSION}}")
            implementation("io.github.darkryh.dispatch:dispatch-koin:{{DISPATCH_VERSION}}")

            // Silence SLF4J's "no providers" notice so it can't corrupt the terminal UI.
            runtimeOnly("org.slf4j:slf4j-nop:2.0.18")
        }

        kotlin {
            jvmToolchain({{JVM}})
        }

        application {
            mainClass.set("{{PACKAGE}}.MainKt")
            applicationDefaultJvmArgs = listOf("-Dfile.encoding=utf-8")
        }
        """.trimIndent() + "\n"

    private val GITIGNORE =
        """
        .gradle/
        build/
        .idea/
        *.iml
        *.log
        .DS_Store
        """.trimIndent() + "\n"

    private val MAIN_KT =
        """
        package {{PACKAGE}}

        import com.ead.dispatch.koin.KoinViewModelFactory
        import com.ead.dispatch.runtime.DispatchApplication
        import com.ead.dispatch.runtime.ExitKeyBinding
        import com.ead.dispatch.theme.DispatchTheme
        import com.ead.dispatch.viewmodel.ViewModelProviderScope
        import {{PACKAGE}}.di.appModule
        import kotlin.time.Duration.Companion.milliseconds

        /**
         * Entry point. [DispatchApplication] owns the terminal lifecycle; `config { }` declares the app
         * (including the Koin modules), and `content { }` is the Composable root.
         */
        fun main(args: Array<String>) =
            DispatchApplication(args) {
                config {
                    name = "{{ARTIFACT_ID}}"
                    windowTitle = "{{PROJECT_NAME}}"
                    version = "{{APP_VERSION}}"
                    theme = DispatchTheme.Dark
                    targetFps = 60
                    exitKeys(ExitKeyBinding.ctrl("C"))
                    requireExitDoublePress = true
                    exitTimeoutOnDoublePress = 1_500.milliseconds

                    koin { modules(appModule) }
                }

                content {
                    // Provide a Koin-backed ViewModel store so `viewModel()` returns DI-constructed
                    // view-models. (Adding navigation via NavDisplay wires this up for you instead.)
                    ViewModelProviderScope(factory = KoinViewModelFactory()) {
                        MainScreen()
                    }
                }
            }
        """.trimIndent() + "\n"

    private val MAIN_SCREEN_KT =
        """
        package {{PACKAGE}}

        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.DisposableEffect
        import androidx.compose.runtime.collectAsState
        import androidx.compose.runtime.getValue
        import com.ead.dispatch.input.Key
        import com.ead.dispatch.input.asKeyEvent
        import com.ead.dispatch.layout.Column
        import com.ead.dispatch.runtime.LocalKeyboardInterceptor
        import com.ead.dispatch.runtime.LocalTheme
        import com.ead.dispatch.viewmodel.viewModel
        import com.ead.dispatch.widget.Panel
        import com.ead.dispatch.widget.Text

        /**
         * The single screen. It renders [MainViewModel]'s state and turns ↑/↓ key presses into MVI
         * intents through the keyboard interceptor.
         */
        @Composable
        fun MainScreen(viewModel: MainViewModel = viewModel()) {
            val state by viewModel.state.collectAsState()
            val theme = LocalTheme.current
            val interceptor = LocalKeyboardInterceptor.current

            DisposableEffect(interceptor) {
                val dispose =
                    interceptor.register(priority = 10) { raw ->
                        when (raw.asKeyEvent().key) {
                            Key.ArrowUp -> { viewModel.sendIntent(CounterIntent.Increment); true }
                            Key.ArrowDown -> { viewModel.sendIntent(CounterIntent.Decrement); true }
                            else -> false
                        }
                    }
                onDispose { dispose() }
            }

            Panel(title = "{{PROJECT_NAME}}") {
                Column {
                    Text("Welcome to your Dispatch app!", style = theme.primary)
                    Text("Count: {{D}}{state.count}", style = theme.accent)
                    Text("↑ increment  ·  ↓ decrement  ·  Ctrl+C twice to quit", style = theme.muted)
                }
            }
        }
        """.trimIndent() + "\n"

    private val MAIN_VIEW_MODEL_KT =
        """
        package {{PACKAGE}}

        import com.ead.dispatch.viewmodel.MviViewModel

        /** Immutable UI state for [MainScreen]. */
        data class CounterState(val count: Int = 0)

        /** Everything the screen can ask the view-model to do. */
        sealed interface CounterIntent {
            data object Increment : CounterIntent

            data object Decrement : CounterIntent
        }

        /**
         * A minimal [MviViewModel]: it holds a [CounterState] and reduces [CounterIntent]s into new
         * state with `updateState { … }`. This is the architecture Dispatch encourages — grow it by
         * adding intents and state fields, or switch to `FullMviViewModel` when you need side effects.
         */
        class MainViewModel : MviViewModel<CounterState, CounterIntent>(CounterState()) {
            override suspend fun handleIntent(intent: CounterIntent) {
                when (intent) {
                    CounterIntent.Increment -> updateState { it.copy(count = it.count + 1) }
                    CounterIntent.Decrement -> updateState { it.copy(count = it.count - 1) }
                }
            }
        }
        """.trimIndent() + "\n"

    private val APP_MODULE_KT =
        """
        package {{PACKAGE}}.di

        import com.ead.dispatch.koin.dispatchModule
        import {{PACKAGE}}.MainViewModel

        /**
         * Koin wiring. `dispatchModule { }` is the library's DSL; register view-models with
         * `viewModel { }` so one instance is created (and disposed) per screen, and process-wide
         * dependencies with `single { }`.
         */
        val appModule =
            dispatchModule {
                viewModel { MainViewModel() }
            }
        """.trimIndent() + "\n"

    private val README =
        """
        # {{PROJECT_NAME}}

        A terminal UI application built with [Dispatch](https://github.com/darkryh/dispatch) —
        declarative TUI for Kotlin on the Compose runtime.

        ## Run it

        This starter does not bundle the Gradle wrapper jar. Generate the wrapper once (needs a local
        Gradle — `brew install gradle` or `sdk install gradle`), then run:

        ```bash
        gradle wrapper --gradle-version {{GRADLE_VERSION}}
        ./gradlew run
        ```

        - **↑ / ↓** — change the counter
        - **Ctrl+C** (twice) — quit

        ## Project layout

        ```
        build.gradle.kts                       Dispatch dependencies (Maven Central) + Compose compiler
        src/main/kotlin/{{PACKAGE_PATH}}/
          Main.kt                              DispatchApplication bootstrap + Koin + ViewModel store
          MainScreen.kt                        the @Composable screen + keyboard handling
          MainViewModel.kt                     MviViewModel (state + intents)
          di/AppModule.kt                      Koin module (dispatchModule { viewModel { … } })
        ```

        ## Next steps

        - Add more screens and navigate between them with `NavDisplay` (`dispatch-navigation`).
        - Explore every widget in the official sample app.
        - Switch a view-model to `FullMviViewModel` when you need one-off side effects.

        Generated with the Dispatch initializer.
        """.trimIndent() + "\n"
}
