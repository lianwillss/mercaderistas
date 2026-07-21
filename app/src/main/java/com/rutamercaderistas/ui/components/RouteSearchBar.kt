package com.rutamercaderistas.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.res.stringResource
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private fun normalizar(s: String): String {
    val nfkd = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
    return nfkd.replace(Regex("[\\p{M}\\-\\s]"), "").lowercase()
}

private data class RutaResult(val nombre: String, val score: Int)

@Composable
fun RouteSearchBar(
    routes: List<String>,
    recentRoutes: List<String>,
    selectedRoute: String?,
    onRouteSelected: (String) -> Unit,
    onSearchActiveChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var text by remember(selectedRoute) { mutableStateOf(selectedRoute ?: "") }
    var isFocused by remember { mutableStateOf(false) }
    var showDropdown by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current

    val showRecent = isFocused && text.isBlank() && recentRoutes.isNotEmpty()

    val suggestions by remember(routes, text) {
        derivedStateOf {
            if (text.isBlank()) return@derivedStateOf emptyList<String>()
            val query = normalizar(text)
            routes
                .map { r ->
                    val n = normalizar(r)
                    val score = when {
                        n == query -> 3
                        n.startsWith(query) -> 2
                        n.contains(query) -> 1
                        else -> 0
                    }
                    RutaResult(r, score)
                }
                .filter { it.score > 0 }
                .sortedByDescending { it.score }
                .map { it.nombre }
                .distinct()
        }
    }

    LaunchedEffect(isFocused, text, suggestions) {
        val next = when {
            !isFocused -> false
            text.isNotBlank() && suggestions.isEmpty() -> true
            text.isNotBlank() -> suggestions.isNotEmpty()
            else -> recentRoutes.isNotEmpty()
        }
        if (next != showDropdown) {
            onSearchActiveChanged(next)
        }
        showDropdown = next
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = {
                    Text(
                        stringResource(R.string.buscar_ruta),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.buscar_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    text = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.limpiar_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = if (selectedRoute != null && text == selectedRoute)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (text.isNotBlank()) {
                            val matched = routes.firstOrNull { normalizar(it) == normalizar(text) }
                            if (matched != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRouteSelected(matched)
                                focusManager.clearFocus()
                            }
                        }
                    }
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(56.dp)
                    .onFocusChanged { state ->
                        isFocused = state.isFocused
                    }
                    .clip(RoundedCornerShape(28.dp))
                    .shadow(if (isFocused) 4.dp else 1.dp, RoundedCornerShape(28.dp))
            )

            AnimatedVisibility(
                visible = showDropdown,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 4.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        if (showRecent) {
                            item(key = "recent_header") {
                                    Text(
                                        text = stringResource(R.string.recientes),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                                    )
                            }
                            items(items = recentRoutes, key = { it }) { route ->
                                Box(modifier = Modifier.animateItem()) {
                                    SuggestionItem(
                                        text = route,
                                        query = "",
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            text = route
                                            onRouteSelected(route)
                                            focusManager.clearFocus()
                                        }
                                    )
                                }
                            }
                        } else if (text.isNotBlank() && suggestions.isEmpty()) {
                            item(key = "empty") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Outlined.Search,
                                            contentDescription = stringResource(R.string.buscar_cd),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = stringResource(R.string.sin_rutas),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        } else {
                            items(items = suggestions, key = { it }) { route ->
                                Box(modifier = Modifier.animateItem()) {
                                    SuggestionItem(
                                        text = route,
                                        query = text,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            text = route
                                            onRouteSelected(route)
                                            focusManager.clearFocus()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionItem(
    text: String,
    query: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = stringResource(R.string.buscar_cd),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = highlightQuery(text, query),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun highlightQuery(text: String, query: String): androidx.compose.ui.text.AnnotatedString {
    if (query.isBlank()) return buildAnnotatedString { append(text) }
    val range = findMatchRange(text, query) ?: return buildAnnotatedString { append(text) }
    val highlightColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        append(text.substring(0, range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor)) {
            append(text.substring(range.first, range.last))
        }
        append(text.substring(range.last))
    }
}

private fun findMatchRange(original: String, query: String): IntRange? {
    val normOriginal = normalizar(original)
    val normQuery = normalizar(query)
    val normStart = normOriginal.indexOf(normQuery)
    if (normStart < 0) return null

    var origIdx = 0
    var normIdx = 0
    while (normIdx < normStart && origIdx < original.length) {
        if (Character.isLetterOrDigit(original[origIdx])) normIdx++
        origIdx++
    }
    val matchStart = origIdx

    while (normIdx < normStart + normQuery.length && origIdx < original.length) {
        if (Character.isLetterOrDigit(original[origIdx])) normIdx++
        origIdx++
    }
    return matchStart until origIdx
}

@Preview(showBackground = true)
@Composable
private fun RouteSearchBarPreview() {
    if (BuildConfig.DEBUG) {
        com.rutamercaderistas.ui.theme.MercaderistasTheme {
            RouteSearchBar(
                routes = listOf("Ruta Norte", "Ruta Sur", "Ruta Centro"),
                recentRoutes = listOf("Ruta Norte"),
                selectedRoute = null,
                onRouteSelected = {},
                onSearchActiveChanged = {},
            )
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RouteSearchBarPreviewDark() {
    if (BuildConfig.DEBUG) {
        RouteSearchBarPreview()
    }
}
