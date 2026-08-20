package com.rutamercaderistas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.R
import com.rutamercaderistas.ui.theme.AccentBlue
import com.rutamercaderistas.ui.theme.AccentGreen
import com.rutamercaderistas.ui.theme.AccentOrange
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.StoreColorFuchsia
import com.rutamercaderistas.ui.theme.StoreColorPurple
import com.rutamercaderistas.ui.theme.StoreColorRed
import com.rutamercaderistas.ui.theme.rs
import androidx.compose.ui.graphics.Color

data class CodProvItem(val nameRes: Int, val code: String)

val CodProvItems = listOf(
    CodProvItem(R.string.cod_prov_nat_natural, "13710"),
    CodProvItem(R.string.cod_prov_suk, "7088"),
    CodProvItem(R.string.cod_prov_fermentista, "1000339140"),
    CodProvItem(R.string.cod_prov_love_co, "76834823"),
    CodProvItem(R.string.cod_prov_cuk, "76381134 / 76381134-4"),
    CodProvItem(R.string.cod_prov_bymaria, "76371495"),
    CodProvItem(R.string.cod_prov_global_retail, "76448717"),
    CodProvItem(R.string.cod_prov_caso, "5591"),
    CodProvItem(R.string.cod_prov_ecocultiva, "11014"),
    CodProvItem(R.string.cod_prov_dusoleil, "11546"),
    CodProvItem(R.string.cod_prov_olimpia, "1000325189"),
)

private val avatarColors = listOf(
    AccentBlue,
    AccentGreen,
    AccentOrange,
    StoreColorFuchsia,
    StoreColorPurple,
    StoreColorRed,
)

private fun avatarColor(index: Int): Color = avatarColors[index % avatarColors.size]

@Composable
fun CodProvModal(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    IosModal(
        visible = visible,
        onDismiss = onDismiss,
        title = stringResource(R.string.cod_prov_title),
        subtitle = stringResource(R.string.cod_prov_subtitle),
    ) {
        val dimens = LocalAppDimens.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            CodProvItems.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (index % 2 == 0) {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            } else MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.medium,
                        )
                        .padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp * rs())
                            .clip(CircleShape)
                            .background(avatarColor(index).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Badge,
                            contentDescription = null,
                            tint = avatarColor(index),
                            modifier = Modifier.size(20.dp * rs()),
                        )
                    }
                    Spacer(modifier = Modifier.width(dimens.spacingMd))
                    Text(
                        text = stringResource(item.nameRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = item.code,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
        }
    }
}