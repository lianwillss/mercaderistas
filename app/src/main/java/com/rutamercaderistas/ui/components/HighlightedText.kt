package com.rutamercaderistas.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import com.rutamercaderistas.services.compactNorm

/**
 * Texto con la parte coincidente resaltada (tolerante a tildes y espacios,
 * misma lógica que la búsqueda). Lo coincidente va en primario + bold.
 */
@Composable
fun HighlightedText(
    text: String,
    query: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val tokens = remember(query) {
        compactNorm(query).split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
    }
    if (tokens.isEmpty() || text.isBlank()) {
        Text(text = text, style = style, color = color, modifier = modifier, maxLines = maxLines, overflow = overflow)
        return
    }
    Text(
        text = highlightRanges(text, tokens, MaterialTheme.colorScheme.primary),
        style = style,
        color = color,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
    )
}

/** AnnotatedString con los rangos coincidentes en [highlightColor] + bold. */
fun highlightRanges(text: String, tokens: List<String>, highlightColor: Color): AnnotatedString {
    val ranges = computeHighlightRanges(text, tokens)
    return buildAnnotatedString {
        if (ranges.isEmpty()) {
            append(text)
        } else {
            var cursor = 0
            for (r in ranges) {
                if (r.first > cursor) append(text.substring(cursor, r.first))
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor))
                append(text.substring(r.first, r.last + 1))
                pop()
                cursor = r.last + 1
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }
}

fun computeHighlightRanges(text: String, tokens: List<String>): List<IntRange> {
    val normBuilder = StringBuilder()
    val map = mutableListOf<IntRange>()
    text.forEachIndexed { i, c ->
        val n = compactNorm(c.toString())
        if (n.isNotEmpty()) {
            map.add(i..i)
            normBuilder.append(n)
        }
    }
    val norm = normBuilder.toString()
    val found = mutableListOf<IntRange>()
    for (tok in tokens) {
        if (tok.isBlank()) continue
        var from = 0
        while (from <= norm.length - tok.length) {
            val idx = norm.indexOf(tok, from)
            if (idx < 0) break
            val startOrig = map[idx].first
            val endOrig = map[idx + tok.length - 1].last
            found.add(startOrig..endOrig)
            from = idx + tok.length
        }
    }
    found.sortBy { it.first }
    val merged = mutableListOf<IntRange>()
    for (r in found) {
        val last = merged.lastOrNull()
        if (last != null && r.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, r.last)
        } else {
            merged.add(r)
        }
    }
    return merged
}
