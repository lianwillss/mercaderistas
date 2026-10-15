package com.rutamercaderistas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.R
import com.rutamercaderistas.ui.components.CodProvItems
import com.rutamercaderistas.ui.components.CodProvRow
import com.rutamercaderistas.ui.components.DropletToast
import com.rutamercaderistas.ui.components.ScreenHeader
import com.rutamercaderistas.ui.components.copyCodProvToClipboard
import com.rutamercaderistas.ui.components.filterCodProvItems
import com.rutamercaderistas.ui.theme.ComponentShapes
import com.rutamercaderistas.ui.theme.LocalAppDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodProvScreen(
    onBack: () -> Unit,
) {
    val dimens = LocalAppDimens.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var copyToast by remember { mutableStateOf<String?>(null) }
    val copiedMessage = stringResource(R.string.cod_prov_copiado)
    val buscarCd = stringResource(R.string.cod_prov_buscar)

    // Toast-gota hermano en Box (nunca hijo de un Column con peso).
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            ScreenHeader(
                onBack = onBack,
                title = stringResource(R.string.cod_prov_title),
                scrollBehavior = scrollBehavior,
            )

            Text(
                text = stringResource(R.string.cod_prov_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = dimens.spacingLg, vertical = dimens.spacingSm),
            )

            TextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(buscarCd) },
                placeholder = { Text(buscarCd) },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        val limpiarCd = stringResource(R.string.limpiar_busqueda_cd)
                        IconButton(
                            onClick = { query = "" },
                            modifier = Modifier.semantics { contentDescription = limpiarCd }
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = ComponentShapes.textField,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spacingMd)
                    .semantics { contentDescription = buscarCd },
            )

            val names = CodProvItems.associateWith { stringResource(it.nameRes) }
            val filtered = remember(query) {
                filterCodProvItems(CodProvItems, query) { names.getValue(it) }
            }
            if (query.isNotBlank()) {
                Text(
                    text = stringResource(R.string.cod_prov_filtradas, filtered.size, CodProvItems.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = dimens.spacingLg, vertical = dimens.spacingXs),
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(
                    start = dimens.spacingMd,
                    end = dimens.spacingMd,
                    top = dimens.spacingXs,
                    bottom = dimens.scrollBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(
                    items = filtered,
                    key = { _, item -> item.code + "|" + item.nameRes },
                    contentType = { _, _ -> "codprov" },
                ) { index, item ->
                    val name = names.getValue(item)
                    CodProvRow(
                        item = item,
                        name = name,
                        index = index,
                        copyCodeLabel = stringResource(R.string.cod_prov_copiar_codigo, item.code),
                        rowDescription = stringResource(R.string.cod_prov_fila_cd, name, item.code),
                        onCopyCode = {
                            copyCodProvToClipboard(context, item.code)
                            copyToast = copiedMessage
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
        DropletToast(
            message = copyToast,
            onTimeout = { copyToast = null },
        )
    }
}
