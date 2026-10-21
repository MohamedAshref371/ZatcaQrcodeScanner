package com.poetscorner.invoiceqr.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDirection

@Composable
internal fun RawQrText(raw: String, modifier: Modifier = Modifier) {
    BasicTextField(
        value = raw,
        onValueChange = {},
        modifier = modifier,
        readOnly = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurface,
            lineBreak = LineBreak.Simple,
            textDirection = TextDirection.Ltr
        ),
        visualTransformation = QrCharacterWrap
    )
}

// Allow wrapping between any two characters, rather than preferring breaks at '/'.
// Only the display is transformed: selection and copying still use the original QR.
internal object QrCharacterWrap : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val display = AnnotatedString(text.text.toList().joinToString("\u200B"))
        return TransformedText(display, object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                (offset * 2).coerceAtMost(display.length)

            override fun transformedToOriginal(offset: Int): Int =
                ((offset + 1) / 2).coerceAtMost(text.length)
        })
    }
}
