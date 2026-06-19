@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.width
import com.ead.dispatch.widget.LoadingIndicator
import com.ead.dispatch.widget.ProgressBar
import com.ead.dispatch.widget.ProgressBarStyle
import com.ead.dispatch.widget.SectionHeader
import com.ead.dispatch.widget.Spinner
import com.ead.dispatch.widget.SpinnerStyle
import com.ead.dispatch.widget.Text
import com.ead.dispatch.widget.TransferProgress
import kotlinx.coroutines.delay

@Composable
internal fun ProgressGallery() {
    val styles = ProgressBarStyle.entries

    // One progress value per bar, each filling at its own rate then snapping back to 0 and looping.
    val progress = remember { mutableStateListOf<Float>().apply { repeat(styles.size) { add(0f) } } }
    // Shared animation frame for the spinners / loading indicator.
    var frame by remember { mutableStateOf(0) }
    // An independent transfer that also loops.
    var transfer by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(90)
            frame += 1
            for (index in styles.indices) {
                // Each bar advances at a different frequency (faster the further down it is).
                val step = 0.012f * (index + 1)
                val next = progress[index] + step
                progress[index] = if (next >= 1f) 0f else next
            }
            val nextTransfer = transfer + 0.02f
            transfer = if (nextTransfer >= 1f) 0f else nextTransfer
        }
    }

    GalleryScreen("Progress", "Live, looping progress — each bar advances at its own rate") {
        SectionHeader("Progress bars")
        styles.forEachIndexed { index, style ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(style.name.padEnd(8))
                ProgressBar(
                    progress = progress[index],
                    modifier = Modifier.fillMaxWidth(),
                    style = style,
                    showPercentage = true,
                )
            }
        }
        SectionHeader("Spinners")
        Row {
            SpinnerStyle.entries.forEach { style ->
                Spinner(frame = frame, style = style)
                Spacer(Modifier.width(2))
            }
        }
        LoadingIndicator(frame = frame, text = "Refreshing catalogue")
        TransferProgress(
            progress = transfer,
            bytesTransferred = (transfer * 10_000_000).toLong(),
            totalBytes = 10_000_000,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
