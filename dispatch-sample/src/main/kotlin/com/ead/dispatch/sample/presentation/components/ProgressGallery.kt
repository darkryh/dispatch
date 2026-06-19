@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.components

import androidx.compose.runtime.Composable
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

@Composable
internal fun ProgressGallery() {
    GalleryScreen("Progress", "Determinate and indeterminate progress presentations") {
        SectionHeader("Progress bars")
        ProgressBarStyle.entries.forEachIndexed { index, style ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(style.name.padEnd(8))
                ProgressBar(
                    progress = (index + 2) / 8f,
                    modifier = Modifier.fillMaxWidth(),
                    style = style,
                    showPercentage = true,
                )
            }
        }
        SectionHeader("Spinners")
        Row {
            SpinnerStyle.entries.forEach { style ->
                Spinner(frame = 3, style = style)
                Spacer(Modifier.width(2))
            }
        }
        LoadingIndicator(frame = 4, text = "Refreshing catalogue")
        TransferProgress(
            progress = 0.64f,
            bytesTransferred = 6_400_000,
            totalBytes = 10_000_000,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
