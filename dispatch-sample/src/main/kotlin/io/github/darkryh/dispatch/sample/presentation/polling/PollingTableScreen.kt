@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.presentation.polling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.ajalt.mordant.rendering.TextAlign
import io.github.darkryh.dispatch.input.Key
import io.github.darkryh.dispatch.input.asKeyEvent
import io.github.darkryh.dispatch.layout.Box
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxSize
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.weight
import io.github.darkryh.dispatch.navigation.LocalNavigator
import io.github.darkryh.dispatch.runtime.LocalKeyboardInterceptor
import io.github.darkryh.dispatch.runtime.LocalTerminalHeight
import io.github.darkryh.dispatch.runtime.LocalTheme
import io.github.darkryh.dispatch.sample.designsystem.AppKeyPriority
import io.github.darkryh.dispatch.sample.designsystem.TitleBanner
import io.github.darkryh.dispatch.sample.designsystem.wrapIndex
import io.github.darkryh.dispatch.widget.DividerStyle
import io.github.darkryh.dispatch.widget.HorizontalDivider
import io.github.darkryh.dispatch.widget.Panel
import io.github.darkryh.dispatch.widget.Table
import io.github.darkryh.dispatch.widget.TableColumn
import io.github.darkryh.dispatch.widget.TableColumnWidth
import io.github.darkryh.dispatch.widget.Text
import kotlinx.coroutines.delay

/** Screen title, rendered by the shared [TitleBanner] like every other sample screen. */
private const val POLLING_TITLE = "Polling Table"

/**
 * The screen's subtitle, and the anchor the PTY reliability harness waits on. Nothing else in the
 * sample prints this line, so matching it proves this screen — and only this screen — is painted.
 */
private const val POLLING_ANCHOR = "Katalyst-shaped polling monitor"

/** Subtitle line: the harness anchor plus the key legend, kept short so it never wraps. */
private const val POLLING_SUBTITLE = "$POLLING_ANCHOR · ↑↓ move · Enter detail · ← back"

/** How often the background poll publishes a fresh snapshot, independently of any keypress. */
private const val POLL_INTERVAL_MILLIS = 1_500L

/** Monitored services. Deliberately more than any viewport holds, so the table always windows. */
private const val SERVICE_COUNT = 36

/**
 * Lines the fixed chrome costs before any data row: 3 banner (title, subtitle, rule), 1 chrome row,
 * 2 rules, 1 footer, 1 table header, plus 1 spare so a full frame never overflows the viewport and
 * scrolls the terminal.
 */
private const val RESERVED_LINES = 9

/** Never window the table below this, however short the terminal is. */
private const val MIN_VISIBLE_ROWS = 3

/** Latency of the first row, in milliseconds; each subsequent row adds [LATENCY_STEP_MILLIS]. */
private const val BASE_LATENCY_MILLIS = 11

/** Per-row latency increment, in milliseconds. */
private const val LATENCY_STEP_MILLIS = 7

/** Base age of a row's "last seen" cell, in seconds. */
private const val BASE_AGE_SECONDS = 3

/** Per-row "last seen" age increment, in seconds. */
private const val AGE_STEP_SECONDS = 7

private const val BYTES_PER_MEGABYTE = 1024L * 1024L
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L

/** Regions cycled across the fleet, so the sort key is not simply the row order. */
private val REGIONS =
    listOf("us-east-1", "us-west-2", "eu-central-1", "eu-west-1", "ap-south-1", "sa-east-1")

/** Health labels cycled across the fleet. */
private val STATUSES = listOf("healthy", "degraded", "healthy", "draining")

/**
 * One immutable poll result. The background poll replaces the whole object every
 * [POLL_INTERVAL_MILLIS], which is what makes the frame change on its own cadence.
 *
 * @param tick Number of completed polls; also drives the uptime readout.
 * @param gauge The footer string (heap + uptime + poll count).
 * @param freshRow Index of the single row whose "last seen" cell reads "just now" this tick.
 */
private data class PollSnapshot(
    val tick: Int,
    val gauge: String,
    val freshRow: Int,
)

/** One monitored service, rebuilt from scratch on every recomposition. */
private data class ServiceRow(
    val id: Int,
    val service: String,
    val region: String,
    val status: String,
    val latencyMs: Int,
    val lastSeen: String,
)

/**
 * A polling fleet monitor laid out as one full-height [Column] — banner, chrome row, rule, navigable
 * content, rule, footer — deliberately **without** [io.github.darkryh.dispatch.layout.TerminalScreen]
 * or [io.github.darkryh.dispatch.sample.designsystem.AppScaffold].
 *
 * It reproduces the shape a real downstream Dispatch application has, because that shape is what the
 * render-reliability harness has to keep honest:
 * - the row list is **re-allocated and re-sorted on every recomposition**, so no frame reuses the
 *   previous frame's objects;
 * - a background poll publishes a fresh immutable [PollSnapshot] every [POLL_INTERVAL_MILLIS],
 *   rewriting the footer gauge and one table cell whether or not a key was pressed;
 * - ↑/↓ move the selection while ← is "back", so a burst of arrow keys mixes cheap selection
 *   repaints with a content transition — some presses change the whole content pane, most do not.
 *
 * The screen owns its keys at [AppKeyPriority.POLLING_TABLE]: there is no scaffold here, so ← is the
 * only way out (it closes the detail pane first, then pops the back stack).
 */
@Composable
fun PollingTableScreen() {
    val theme = LocalTheme.current
    val navigator = LocalNavigator.current
    val interceptor = LocalKeyboardInterceptor.current
    val terminalHeight = LocalTerminalHeight.current

    var snapshot by remember { mutableStateOf(pollSnapshot(0)) }
    var selected by remember { mutableStateOf(0) }
    var detailOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(POLL_INTERVAL_MILLIS)
            snapshot = pollSnapshot(snapshot.tick + 1)
        }
    }

    DisposableEffect(interceptor) {
        val dispose =
            interceptor.register(priority = AppKeyPriority.POLLING_TABLE) { rawEvent ->
                when (rawEvent.asKeyEvent().key) {
                    Key.ArrowUp -> {
                        selected = wrapIndex(selected, -1, SERVICE_COUNT)
                        true
                    }
                    Key.ArrowDown -> {
                        selected = wrapIndex(selected, 1, SERVICE_COUNT)
                        true
                    }
                    Key.Enter -> {
                        detailOpen = true
                        true
                    }
                    Key.ArrowLeft -> {
                        if (detailOpen) detailOpen = false else navigator.popBackStack()
                        true
                    }
                    else -> false
                }
            }
        onDispose { dispose() }
    }

    // Never remembered: a fresh, freshly sorted list per recomposition, exactly like the app whose
    // flicker this scenario reproduces.
    val rows = serviceRows(snapshot)

    Column(modifier = Modifier.fillMaxSize()) {
        TitleBanner(title = POLLING_TITLE, subtitle = POLLING_SUBTITLE)
        ChromeRow(rowCount = rows.size, selected = selected, snapshot = snapshot)
        HorizontalDivider(style = DividerStyle.Light, modifier = Modifier.fillMaxWidth())
        Box(modifier = Modifier.weight(1f)) {
            if (detailOpen) {
                ServiceDetail(rows[selected])
            } else {
                ServiceTable(rows = rows, selected = selected, terminalHeight = terminalHeight)
            }
        }
        HorizontalDivider(style = DividerStyle.Light, modifier = Modifier.fillMaxWidth())
        Text(snapshot.gauge, style = theme.muted)
    }
}

/** The static-looking status strip between the banner and the content pane. */
@Composable
private fun ChromeRow(
    rowCount: Int,
    selected: Int,
    snapshot: PollSnapshot,
) {
    val theme = LocalTheme.current
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("fleet", style = theme.accent)
        Text("  services $rowCount", style = theme.muted)
        Text("  ·  selected ${selected + 1}/$rowCount", style = theme.info)
        Text("  ·  refresh $POLL_INTERVAL_MILLIS ms", style = theme.muted)
        Text("  ·  tick ${snapshot.tick}", style = theme.muted)
    }
}

/** The content pane's default view: a window of the fleet centred on the selected row. */
@Composable
private fun ServiceTable(
    rows: List<ServiceRow>,
    selected: Int,
    terminalHeight: Int,
) {
    val capacity = (terminalHeight - RESERVED_LINES).coerceIn(MIN_VISIBLE_ROWS, rows.size)
    val first = (selected - capacity / 2).coerceIn(0, rows.size - capacity)
    Table(
        items = rows.subList(first, first + capacity),
        columns = tableColumns(rows[selected].id),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The content pane after Enter: the selected row expanded, dismissed with ←. */
@Composable
private fun ServiceDetail(row: ServiceRow) {
    val theme = LocalTheme.current
    Panel(modifier = Modifier.fillMaxWidth(), title = "${row.service}  ·  ${row.region}") {
        Column {
            Text("status     ${row.status}", style = theme.primary)
            Text("latency    ${row.latencyMs} ms", style = theme.info)
            Text("last seen  ${row.lastSeen}", style = theme.muted)
            Text("press ← to return to the table", style = theme.muted)
        }
    }
}

/** Column definitions, rebuilt per recomposition because the cursor column closes over the selection. */
private fun tableColumns(selectedId: Int): List<TableColumn<ServiceRow>> =
    listOf(
        TableColumn(
            header = " ",
            width = TableColumnWidth.Fixed(1),
            valueOf = { if (it.id == selectedId) "❯" else " " },
        ),
        TableColumn(header = "Service", valueOf = ServiceRow::service),
        TableColumn(header = "Region", width = TableColumnWidth.Weight(1f), valueOf = ServiceRow::region),
        TableColumn(header = "Status", valueOf = ServiceRow::status),
        TableColumn(header = "Latency", align = TextAlign.RIGHT, valueOf = { "${it.latencyMs} ms" }),
        TableColumn(header = "Last seen", width = TableColumnWidth.Weight(1f), valueOf = ServiceRow::lastSeen),
    )

/**
 * Builds the fleet from scratch and re-sorts it. Called from the composition body on **every**
 * recomposition — the allocation and the sort are the point, not an oversight.
 */
private fun serviceRows(snapshot: PollSnapshot): List<ServiceRow> =
    (0 until SERVICE_COUNT)
        .map { index ->
            val region = REGIONS[index % REGIONS.size]
            ServiceRow(
                id = index,
                service = "svc-%02d".format(index),
                region = region,
                status = STATUSES[index % STATUSES.size],
                latencyMs = BASE_LATENCY_MILLIS + index * LATENCY_STEP_MILLIS,
                lastSeen =
                    if (index == snapshot.freshRow) {
                        "just now"
                    } else {
                        "${BASE_AGE_SECONDS + index * AGE_STEP_SECONDS}s ago"
                    },
            )
        }.sortedBy { "${it.region}/${it.service}" }

/** Produces the immutable snapshot the background poll publishes: a live heap/uptime gauge. */
private fun pollSnapshot(tick: Int): PollSnapshot {
    val runtime = Runtime.getRuntime()
    val heapMegabytes = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MEGABYTE
    val uptimeSeconds = tick * POLL_INTERVAL_MILLIS / MILLIS_PER_SECOND
    return PollSnapshot(
        tick = tick,
        gauge = "heap $heapMegabytes MB  ·  uptime ${formatUptime(uptimeSeconds)}  ·  poll #$tick",
        freshRow = tick % SERVICE_COUNT,
    )
}

/** Formats an elapsed second count as `mm:ss`. */
private fun formatUptime(totalSeconds: Long): String =
    "%02d:%02d".format(totalSeconds / SECONDS_PER_MINUTE, totalSeconds % SECONDS_PER_MINUTE)
