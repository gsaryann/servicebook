package com.servicebook.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Visual transformation for 10-digit Russian phone number: (XXX) XXX-XX-XX
 * Text input contains only raw digits (e.g. "9991234567").
 */
class PhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = if (text.text.length > 10) text.text.take(10) else text.text
        val out = StringBuilder()

        for (i in raw.indices) {
            if (i == 0) out.append("(")
            out.append(raw[i])
            if (i == 2) out.append(") ")
            else if (i == 5 || i == 7) out.append("-")
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, raw.length)
                return when {
                    clamped <= 0 -> 0
                    clamped <= 2 -> clamped + 1 // 1 -> 2, 2 -> 3
                    clamped <= 5 -> clamped + 3 // 3 -> 6, 4 -> 7, 5 -> 8
                    clamped <= 7 -> clamped + 4 // 6 -> 10, 7 -> 11
                    clamped <= 10 -> clamped + 5 // 8 -> 13, 9 -> 14, 10 -> 15
                    else -> out.length
                }.coerceIn(0, out.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, out.length)
                return when {
                    clamped <= 1 -> 0
                    clamped <= 3 -> clamped - 1
                    clamped <= 6 -> 3
                    clamped <= 8 -> clamped - 3
                    clamped <= 10 -> 6
                    clamped <= 12 -> clamped - 4
                    clamped <= 13 -> 8
                    clamped <= 15 -> clamped - 5
                    else -> raw.length
                }.coerceIn(0, raw.length)
            }
        }

        return TransformedText(AnnotatedString(out.toString()), offsetMapping)
    }
}

/**
 * Visual transformation for 8-digit date: DD.MM.YYYY
 * Text input contains only raw digits (e.g. "15051995").
 */
class DateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = if (text.text.length > 8) text.text.take(8) else text.text
        val out = StringBuilder()

        for (i in raw.indices) {
            out.append(raw[i])
            if (i == 1 || i == 3) {
                out.append(".")
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, raw.length)
                return when {
                    clamped <= 1 -> clamped
                    clamped <= 3 -> clamped + 1
                    else -> clamped + 2
                }.coerceIn(0, out.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, out.length)
                return when {
                    clamped <= 2 -> clamped.coerceAtMost(2)
                    clamped <= 5 -> (clamped - 1).coerceAtMost(4)
                    else -> clamped - 2
                }.coerceIn(0, raw.length)
            }
        }

        return TransformedText(AnnotatedString(out.toString()), offsetMapping)
    }
}
