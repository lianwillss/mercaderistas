package com.rutamercaderistas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.R
import com.rutamercaderistas.ui.components.CodProvItems
import com.rutamercaderistas.ui.components.ScreenHeader
import com.rutamercaderistas.ui.theme.AccentBlue
import com.rutamercaderistas.ui.theme.AccentGreen
import com.rutamercaderistas.ui.theme.AccentOrange
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.StoreColorFuchsia
import com.rutamercaderistas.ui.theme.StoreColorPurple
import com.rutamercaderistas.ui.theme.StoreColorRed
import com.rutamercaderistas.ui.theme.rs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodProvScreen(
    onBack: () -> Unit,
) {
    val dimens = LocalAppDimens.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            CodProvItems.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
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

private val avatarColors = listOf(
    AccentBlue,
    AccentGreen,
    AccentOrange,
    StoreColorFuchsia,
    StoreColorPurple,
    StoreColorRed,
)

private fun avatarColor(index: Int): Color = avatarColors[index % avatarColors.size]