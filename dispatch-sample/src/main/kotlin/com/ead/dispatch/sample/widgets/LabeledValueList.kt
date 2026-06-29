package com.ead.dispatch.sample.widgets

import androidx.compose.runtime.Composable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.width
import com.ead.dispatch.widget.Text
import com.github.ajalt.mordant.rendering.TextStyle

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
