# Dispatch Initializr

A [Spring-Initializr](https://start.spring.io)-style project generator for **Dispatch**, written
fully in Kotlin (Kotlin/Wasm + Compose Multiplatform) and hosted as a static site on GitHub Pages.

Fill in a project name, group/artifact id, package and version → download a ready-to-run Dispatch
app, zipped entirely in the browser. There is **no backend**: the whole thing is static files.

## Architecture

```
initializr/                         standalone Gradle build (NOT in the root settings.gradle.kts)
  src/commonMain/kotlin/.../
    model/        ProjectConfig, FormState, Derivation, validation   pure, JVM-unit-tested
    template/     StarterTemplate (placeholdered)                    the minimal Dispatch app, as data
    generate/     ProjectGenerator (substitution)                    {{TOKEN}} -> user values
    zip/          Crc32, ZipArchive (STORED), Base64                 pure-Kotlin zipping, no JS library
  src/commonTest/kotlin/            GenerationTest, DerivationTest    run on the JVM via `jvmTest`
  src/commonMain/composeResources/font/  JetBrains Mono (bundled)    consistent box-drawing glyphs
  src/wasmJsMain/kotlin/.../
    Main.kt                         ComposeViewport entry + prefers-reduced-motion
    ui/Theme.kt                     palette (twin of DispatchTheme.Dark), tempo, font, locals
    ui/Motion.kt                    typewriter, blinking cursor, spinner, scanlines (reduced-motion aware)
    ui/Wordmark.kt                  the DISPATCH ASCII figlet
    ui/Components.kt                terminal panel, prompt field, file tree, bracket button, status bar
    ui/BuildConsole.kt              the "build log" generate animation
    ui/InitializrApp.kt             the terminal window: title bar, boot, form, output, actions
    platform/Download.kt            the ONLY browser interop (data-URL anchor download)
```

**User-facing form** collects just **Project name + Package + Version**; `groupId`/`artifactId` are
derived live (`com.example.myapp` → `com.example` : `my-dispatch-app`) with an `▸ advanced` override.
The generator still receives the full five-field `ProjectConfig` — see `model/Derivation.kt`.

**Design principles**

- **Template as data.** The starter app lives as a typed `List<TemplateFile>` with `{{PLACEHOLDER}}`
  tokens — type-checked, and substituted deterministically. A unit test asserts the output references
  the real Maven Central coordinates and leaves no placeholder behind.
- **Pure core, thin shell.** Generation, substitution, zipping and Base64 are all in `commonMain` and
  tested on the JVM. The browser layer is a single anchor-click in `Download.kt`.
- **Self-contained ZIP.** `ZipArchive` writes a standard STORED-method archive (with `Crc32`) — no JS
  zip dependency, no native code.
- **Standalone build.** Not included in the root `settings.gradle.kts`, so the Compose-Multiplatform/
  Wasm toolchain never slows down a plain `./gradlew build` of the library.
- **Terminal aesthetic, inherited.** The page renders as a terminal window (monospace, box-drawing,
  ANSI palette twinned from `DispatchTheme.Dark`, ASCII `DISPATCH` wordmark, a `~/dispatch ❯` prompt).
  JetBrains Mono is bundled so box-drawing/braille glyphs render identically across browsers.
- **Tasteful, accessible motion.** A boot typewriter, blinking cursor and a "build log" generate
  animation — all behind a single `LocalReducedMotion` switch seeded from `prefers-reduced-motion`,
  and the download never depends on any animation.

## Build & run locally

```bash
# From the repository root:
./gradlew -p initializr jvmTest                    # fast unit tests of the generator
./gradlew -p initializr wasmJsBrowserDistribution  # build the static site
./gradlew -p initializr wasmJsBrowserDevelopmentRun # serve with hot reload at http://localhost:8080
```

The production distribution lands in `initializr/build/dist/wasmJs/productionExecutable/`.

## Deploy (GitHub Pages)

Deployment is automated by [`.github/workflows/initializr-pages.yml`](../.github/workflows/initializr-pages.yml):
it builds the Wasm distribution and publishes it to Pages on every push under `initializr/`.

**One-time setup:** in the repository's **Settings → Pages**, set **Source = GitHub Actions**.

## Notes & trade-offs

- **The generated project depends on published artifacts.** It references
  `io.github.darkryh.dispatch:*:<version>` from Maven Central. Until the library is actually published
  (it is currently on a `-SNAPSHOT`), the download will not resolve. When you publish, bump the single
  constant `StarterTemplate.DISPATCH_VERSION`.
- **Bundle size.** Compose Multiplatform renders via Skia, so the site ships an ~8 MB `skiko.wasm`
  (cached after first load). If a smaller footprint matters more than the Compose-canvas look, the same
  Kotlin UI code could target **Kotlin/JS + Compose HTML** (DOM-based, no Skia) — a drop-in alternative
  that keeps everything in Kotlin.
- **No Gradle wrapper jar** is shipped in the generated project (binary blobs don't belong in a text
  template); its README instructs a one-time `gradle wrapper --gradle-version <v>`.
