package io.github.darkryh.dispatch.initializr.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.darkryh.dispatch.initializr.generate.ProjectGenerator
import io.github.darkryh.dispatch.initializr.model.ConfigField
import io.github.darkryh.dispatch.initializr.model.FormState
import io.github.darkryh.dispatch.initializr.model.ProjectConfigValidator
import io.github.darkryh.dispatch.initializr.model.toProjectConfig
import io.github.darkryh.dispatch.initializr.template.StarterTemplate
import kotlinx.coroutines.delay

/** Root of the terminal-styled initializer. */
@Composable
fun InitializrApp() {
    val fonts = rememberDispatchFonts()
    val reduced = LocalReducedMotion.current
    var booted by remember { mutableStateOf(reduced) }

    LaunchedEffect(reduced) {
        if (!reduced) delay(1500)
        booted = true
    }

    CompositionLocalProvider(LocalTermFonts provides fonts) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Term.backgroundDeep)
                    .scanlines(!reduced)
                    .pointerInput(Unit) { detectTapGestures { booted = true } },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(28.dp))
                TerminalWindow(booted)
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun TerminalWindow(booted: Boolean) {
    val fonts = LocalTermFonts.current
    var form by remember { mutableStateOf(FormState.DEFAULT) }
    var generating by remember { mutableStateOf(false) }
    var generatedName by remember { mutableStateOf<String?>(null) }

    val config = form.toProjectConfig()
    val errors = ProjectConfigValidator.validate(form)
    val files = remember(config) { ProjectGenerator.generate(config) }
    fun errorFor(field: ConfigField): String? = errors.firstOrNull { it.field == field }?.message

    Column(
        modifier =
            Modifier
                .widthIn(max = 900.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .border(1.dp, Term.border)
                .background(Term.background),
    ) {
        TitleBar()

        Column(Modifier.padding(20.dp)) {
            Wordmark()
            Spacer(Modifier.height(10.dp))
            Text(
                "// a project generator for the Dispatch terminal-UI framework",
                color = Term.muted,
                fontFamily = fonts,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(4.dp))
            PromptLine()

            Spacer(Modifier.height(22.dp))

            AnimatedVisibility(
                visible = booted,
                enter = fadeIn() + slideInVertically { it / 8 },
            ) {
                Column {
                    ConfigurePanel(
                        form = form,
                        config = config,
                        errorFor = ::errorFor,
                        onFormChange = { form = it },
                    )
                    Spacer(Modifier.height(18.dp))
                    OutputPanel(
                        coordinates = "${config.groupId}:${config.artifactId} · ${config.appVersion}",
                        rootName = config.artifactId,
                        fileCount = files.size,
                        paths = files.map { it.path },
                    )
                    Spacer(Modifier.height(20.dp))
                    ActionArea(
                        valid = errors.isEmpty(),
                        issues = errors.size,
                        generating = generating,
                        config = config,
                        generatedName = generatedName,
                        onGenerate = { generating = true },
                        onFinished = { name ->
                            generating = false
                            generatedName = name
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        StatusBar(
            ready = errors.isEmpty(),
            issues = errors.size,
            versions = "dispatch ${StarterTemplate.DISPATCH_VERSION} · kotlin ${StarterTemplate.KOTLIN_VERSION}",
        )
    }
}

@Composable
private fun TitleBar() {
    val fonts = LocalTermFonts.current
    Row(
        modifier = Modifier.fillMaxWidth().background(Term.surface).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("dispatch://new", color = Term.accent, fontFamily = fonts, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text("●", color = Term.error.copy(alpha = 0.7f), fontFamily = fonts, fontSize = 11.sp)
        Text(" ●", color = Term.warning.copy(alpha = 0.7f), fontFamily = fonts, fontSize = 11.sp)
        Text(" ●", color = Term.success.copy(alpha = 0.7f), fontFamily = fonts, fontSize = 11.sp)
    }
}

@Composable
private fun PromptLine() {
    val fonts = LocalTermFonts.current
    val typed = rememberTypewriter("init")
    val cursorOn = rememberCursorOn()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("~/dispatch ", color = Term.muted, fontFamily = fonts, fontSize = 13.sp)
        Text("❯ ", color = Term.accent, fontFamily = fonts, fontSize = 13.sp)
        Text(typed, color = Term.primary, fontFamily = fonts, fontSize = 13.sp)
        Text("▌", color = Term.cursor, fontFamily = fonts, fontSize = 13.sp, modifier = Modifier.alpha(if (cursorOn) 1f else 0f))
        Spacer(Modifier.width(12.dp))
        Text("[ kotlin·wasm ]", color = Term.accentDim, fontFamily = fonts, fontSize = 12.sp)
    }
}

@Composable
private fun ConfigurePanel(
    form: FormState,
    config: io.github.darkryh.dispatch.initializr.model.ProjectConfig,
    errorFor: (ConfigField) -> String?,
    onFormChange: (FormState) -> Unit,
) {
    val fonts = LocalTermFonts.current
    TerminalPanel("configure") {
        PromptField(
            label = "project name",
            value = form.projectName,
            onValueChange = { onFormChange(form.copy(projectName = it)) },
            error = errorFor(ConfigField.PROJECT_NAME),
        )
        PromptField(
            label = "package",
            value = form.packageName,
            onValueChange = { onFormChange(form.copy(packageName = it)) },
            error = errorFor(ConfigField.PACKAGE_NAME),
        )
        DerivedLine(config.groupId, config.artifactId)
        PromptField(
            label = "version",
            value = form.appVersion,
            onValueChange = { onFormChange(form.copy(appVersion = it)) },
            error = errorFor(ConfigField.APP_VERSION),
        )

        Spacer(Modifier.height(6.dp))
        Text(
            text = if (form.advancedOpen) "▾ advanced" else "▸ advanced",
            color = Term.muted,
            fontFamily = fonts,
            fontSize = 13.sp,
            modifier = Modifier.clickable { onFormChange(form.copy(advancedOpen = !form.advancedOpen)) },
        )
        AnimatedVisibility(visible = form.advancedOpen) {
            Column {
                Spacer(Modifier.height(6.dp))
                PromptField(
                    label = "group id",
                    value = form.groupIdOverride ?: config.groupId,
                    onValueChange = { onFormChange(form.copy(groupIdOverride = it)) },
                    error = errorFor(ConfigField.GROUP_ID),
                )
                PromptField(
                    label = "artifact id",
                    value = form.artifactIdOverride ?: config.artifactId,
                    onValueChange = { onFormChange(form.copy(artifactIdOverride = it)) },
                    error = errorFor(ConfigField.ARTIFACT_ID),
                )
            }
        }
    }
}

@Composable
private fun OutputPanel(
    coordinates: String,
    rootName: String,
    fileCount: Int,
    paths: List<String>,
) {
    val fonts = LocalTermFonts.current
    TerminalPanel("output · $fileCount files") {
        Text(coordinates, color = Term.muted, fontFamily = fonts, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        FileTree(rootName, paths)
    }
}

@Composable
private fun ActionArea(
    valid: Boolean,
    issues: Int,
    generating: Boolean,
    config: io.github.darkryh.dispatch.initializr.model.ProjectConfig,
    generatedName: String?,
    onGenerate: () -> Unit,
    onFinished: (String) -> Unit,
) {
    val fonts = LocalTermFonts.current
    if (generating) {
        TerminalPanel("build") {
            BuildConsole(config = config, onFinished = onFinished)
        }
        return
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            BracketButton(
                label = if (valid) "[ ⏎ generate project ]" else "[ ✗ $issues to fix ]",
                enabled = valid,
                onClick = onGenerate,
            )
        }
        if (generatedName != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "✓ $generatedName — generated",
                color = Term.success,
                fontFamily = fonts,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
