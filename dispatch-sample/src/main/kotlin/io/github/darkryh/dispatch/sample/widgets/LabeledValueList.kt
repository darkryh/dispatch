@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.widgets

import androidx.compose.runtime.Composable
import com.github.ajalt.mordant.rendering.TextStyle
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.width
import io.github.darkryh.dispatch.widget.Text

data class LabeledValue(
    val label: String,
    val value: String?,
    val labelStyle: TextStyle? = null,
    val valueStyle: TextStyle? = null,
)

@Composable
fun LabeledValueList(
    items: List<LabeledValue>,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle? = null,
    valueStyle: TextStyle? = null,
    labelSuffix: String = ":",
    leftPadding: Int = 2,
    labelSpacing: Int = 1,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        items.forEach { item ->
            val text = item.value?.trim().orEmpty()
            if (text.isBlank()) return@forEach

            Row(modifier = Modifier.fillMaxWidth()) {
                if (leftPadding > 0) {
                    Spacer(Modifier.width(leftPadding))
                }
                Text(
                    text = "${item.label}$labelSuffix",
                    style = item.labelStyle ?: labelStyle,
                )
                if (labelSpacing > 0) {
                    Spacer(Modifier.width(labelSpacing))
                }
                Text(
                    text = text,
                    style = item.valueStyle ?: valueStyle,
                )
            }
        }
    }
}
