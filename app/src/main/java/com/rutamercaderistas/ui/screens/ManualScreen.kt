package com.rutamercaderistas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.R
import com.rutamercaderistas.ui.components.ScreenHeader
import com.rutamercaderistas.ui.theme.AccentGreenText
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.storeColor
import com.rutamercaderistas.ui.theme.storeSoftColor

/**
 * Manual de usuario. Explica los seis módulos actuales (Rutero, Promos,
 * Locales, Stock, Cod. Prov y Cod. EAN), el manual de marcas, los ajustes y
 * el actualizador. Se abre desde el ícono de documento del encabezado.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ManualScreen(
    onClose: () -> Unit
) {
    val dimens = LocalAppDimens.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        ScreenHeader(
            onBack = onClose,
            title = stringResource(R.string.manual_usuario_title),
            scrollBehavior = scrollBehavior,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .padding(bottom = dimens.scrollBottomPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Qué es Mercaderistas ──
            SectionCard(
                title = stringResource(R.string.manual_que_es),
                icon = Icons.Outlined.Info,
                color = MaterialTheme.colorScheme.primary
            ) {
                BodyText(stringResource(R.string.manual_intro))
                SubTitle(stringResource(R.string.manual_modulos))
                BulletPoint(stringResource(R.string.manual_mod_b1))
                BulletPoint(stringResource(R.string.manual_mod_b2))
                BulletPoint(stringResource(R.string.manual_mod_b3))
                BulletPoint(stringResource(R.string.manual_mod_b4))
                BulletPoint(stringResource(R.string.manual_mod_b5))
                BulletPoint(stringResource(R.string.manual_mod_b6))
                ManualNote(stringResource(R.string.manual_funciona_sin_conexion))
            }

            // ── Cómo moverte por la app ──
            SectionCard(
                title = stringResource(R.string.manual_navegacion),
                icon = Icons.Outlined.Menu,
                color = MaterialTheme.colorScheme.tertiary
            ) {
                BodyText(stringResource(R.string.manual_nav_texto))
                BulletPoint(stringResource(R.string.manual_nav_b1))
                BulletPoint(stringResource(R.string.manual_nav_b2))
                BulletPoint(stringResource(R.string.manual_nav_b3))
                BulletPoint(stringResource(R.string.manual_nav_b4))
                BulletPoint(stringResource(R.string.manual_nav_b5))
            }

            // ── Sincronizar la planilla ──
            SectionCard(
                title = stringResource(R.string.manual_sincronizar),
                icon = Icons.Outlined.Refresh,
                color = AccentGreenText
            ) {
                NumberedStep(stringResource(R.string.manual_sync_step1))
                NumberedStep(stringResource(R.string.manual_sync_step2))
                NumberedStep(stringResource(R.string.manual_sync_step3))
                NumberedStep(stringResource(R.string.manual_sync_step4))
                ManualNote(stringResource(R.string.manual_check_datos))
            }

            // ── Ruta y día ──
            SectionCard(
                title = stringResource(R.string.manual_seleccionar_ruta),
                icon = Icons.Outlined.Store,
                color = MaterialTheme.colorScheme.tertiary
            ) {
                NumberedStep(stringResource(R.string.manual_ruta_step1))
                NumberedStep(stringResource(R.string.manual_ruta_step2))
                NumberedStep(stringResource(R.string.manual_ruta_step3))
                NumberedStep(stringResource(R.string.manual_ruta_step4))
            }

            // ── La tarjeta de local ──
            SectionCard(
                title = stringResource(R.string.manual_tarjeta_local),
                icon = Icons.Outlined.Store,
                color = MaterialTheme.colorScheme.primary
            ) {
                BodyText(stringResource(R.string.manual_tarjeta_texto))
                BulletPoint(stringResource(R.string.manual_tarjeta_b1))
                BulletPoint(stringResource(R.string.manual_tarjeta_b2))
                BulletPoint(stringResource(R.string.manual_tarjeta_b3))
                BulletPoint(stringResource(R.string.manual_tarjeta_b4))
                BulletPoint(stringResource(R.string.manual_tarjeta_b5))
                ManualNote(stringResource(R.string.manual_compartir_local))
                ManualNote(stringResource(R.string.manual_compartir_ruta))
            }

            // ── Colores de cadena ──
            SectionCard(
                title = stringResource(R.string.manual_colores_cadena),
                icon = Icons.Outlined.Store,
                color = MaterialTheme.colorScheme.primary
            ) {
                ChainColorRow("Jumbo", storeColor("Jumbo"), storeSoftColor("Jumbo"))
                ChainColorRow("Lider", storeColor("Lider"), storeSoftColor("Lider"))
                ChainColorRow("Santa Isabel / SISA", storeColor("Santa Isabel"), storeSoftColor("Santa Isabel"))
                ChainColorRow("Unimarc", storeColor("Unimarc"), storeSoftColor("Unimarc"))
                ChainColorRow("Tottus", storeColor("Tottus"), storeSoftColor("Tottus"))
                ChainColorRow("Alvi", storeColor("Alvi"), storeSoftColor("Alvi"))
            }

            // ── Marcas prioritarias y normales ──
            SectionCard(
                title = stringResource(R.string.manual_marcas),
                icon = Icons.Outlined.Description,
                color = MaterialTheme.colorScheme.tertiary
            ) {
                SubTitle(stringResource(R.string.manual_prioritarias))
                BulletPoint(stringResource(R.string.manual_prio_b1))
                BulletPoint(stringResource(R.string.manual_prio_b2))
                BulletPoint(stringResource(R.string.manual_prio_b3))
                SubTitle(stringResource(R.string.manual_normales))
                BulletPoint(stringResource(R.string.manual_norm_b1))
                BulletPoint(stringResource(R.string.manual_norm_b2))
                BulletPoint(stringResource(R.string.manual_norm_b3))
            }

            // ── Manual de marcas (PDF) ──
            SectionCard(
                title = stringResource(R.string.manual_abrir_pdf),
                icon = Icons.Outlined.Description,
                color = MaterialTheme.colorScheme.tertiary
            ) {
                NumberedStep(stringResource(R.string.manual_pdf_step1))
                NumberedStep(stringResource(R.string.manual_pdf_step2))
                NumberedStep(stringResource(R.string.manual_pdf_step3))
                NumberedStep(stringResource(R.string.manual_pdf_step4))
                ManualNote(stringResource(R.string.manual_navegacion_pdf))
            }

            // ── Promos ──
            SectionCard(
                title = stringResource(R.string.manual_promociones),
                icon = Icons.Outlined.ShoppingBag,
                color = MaterialTheme.colorScheme.primary
            ) {
                BodyText(stringResource(R.string.manual_promo_texto))
                BulletPoint(stringResource(R.string.manual_promo_b1))
                BulletPoint(stringResource(R.string.manual_promo_b2))
                BulletPoint(stringResource(R.string.manual_promo_b3))
                BulletPoint(stringResource(R.string.manual_promo_b4))
                BulletPoint(stringResource(R.string.manual_promo_b5))
                BulletPoint(stringResource(R.string.manual_promo_b6))
                BulletPoint(stringResource(R.string.manual_promo_b7))
            }

            // ── Locales ──
            SectionCard(
                title = stringResource(R.string.manual_ver_locales),
                icon = Icons.Outlined.LocationOn,
                color = MaterialTheme.colorScheme.primary
            ) {
                NumberedStep(stringResource(R.string.manual_locales_step1))
                NumberedStep(stringResource(R.string.manual_locales_step2))
                NumberedStep(stringResource(R.string.manual_locales_step3))
                NumberedStep(stringResource(R.string.manual_locales_step4))
                NumberedStep(stringResource(R.string.manual_locales_step5))
                NumberedStep(stringResource(R.string.manual_locales_step6))
            }

            // ── Stock ──
            SectionCard(
                title = stringResource(R.string.manual_stock),
                icon = Icons.Outlined.Inventory2,
                color = AccentGreenText
            ) {
                BodyText(stringResource(R.string.manual_stock_texto))
                BulletPoint(stringResource(R.string.manual_stock_b1))
                BulletPoint(stringResource(R.string.manual_stock_b2))
                BulletPoint(stringResource(R.string.manual_stock_b3))
                BulletPoint(stringResource(R.string.manual_stock_b4))
                BulletPoint(stringResource(R.string.manual_stock_b5))
                BulletPoint(stringResource(R.string.manual_stock_b6))
                BulletPoint(stringResource(R.string.manual_stock_b7))
                ManualNote(stringResource(R.string.manual_stock_osa))
            }

            // ── Cod. Prov ──
            SectionCard(
                title = stringResource(R.string.manual_cod_prov),
                icon = Icons.Outlined.Badge,
                color = MaterialTheme.colorScheme.tertiary
            ) {
                NumberedStep(stringResource(R.string.manual_cod_prov_step1))
                NumberedStep(stringResource(R.string.manual_cod_prov_step2))
                NumberedStep(stringResource(R.string.manual_cod_prov_step3))
                NumberedStep(stringResource(R.string.manual_cod_prov_step4))
                NumberedStep(stringResource(R.string.manual_cod_prov_step5))
            }

            // ── Cod. EAN ──
            SectionCard(
                title = stringResource(R.string.manual_ean),
                icon = Icons.Outlined.QrCodeScanner,
                color = MaterialTheme.colorScheme.primary
            ) {
                NumberedStep(stringResource(R.string.manual_ean_step1))
                NumberedStep(stringResource(R.string.manual_ean_step2))
                NumberedStep(stringResource(R.string.manual_ean_step3))
                NumberedStep(stringResource(R.string.manual_ean_step4))
                NumberedStep(stringResource(R.string.manual_ean_step5))
                NumberedStep(stringResource(R.string.manual_ean_step6))
                NumberedStep(stringResource(R.string.manual_ean_step7))
                NumberedStep(stringResource(R.string.manual_ean_step8))
            }

            // ── Buscar en toda la app ──
            SectionCard(
                title = stringResource(R.string.manual_buscar),
                icon = Icons.Outlined.Search,
                color = MaterialTheme.colorScheme.tertiary
            ) {
                BodyText(stringResource(R.string.manual_buscar_texto))
                BulletPoint(stringResource(R.string.manual_buscar_b1))
                BulletPoint(stringResource(R.string.manual_buscar_b2))
                BulletPoint(stringResource(R.string.manual_buscar_b3))
                BulletPoint(stringResource(R.string.manual_buscar_b4))
            }

            // ── Ajustes ──
            SectionCard(
                title = stringResource(R.string.manual_ajustes),
                icon = Icons.Outlined.Settings,
                color = MaterialTheme.colorScheme.primary
            ) {
                BulletPoint(stringResource(R.string.manual_ajustes_b1))
                BulletPoint(stringResource(R.string.manual_ajustes_b2))
                BulletPoint(stringResource(R.string.manual_ajustes_b3))
                BulletPoint(stringResource(R.string.manual_ajustes_b4))
                BulletPoint(stringResource(R.string.manual_ajustes_b5))
                BulletPoint(stringResource(R.string.manual_ajustes_b6))
            }

            // ── Actualizaciones ──
            SectionCard(
                title = stringResource(R.string.manual_actualizaciones),
                icon = Icons.Outlined.SystemUpdate,
                color = AccentGreenText
            ) {
                NumberedStep(stringResource(R.string.manual_upd_step1))
                NumberedStep(stringResource(R.string.manual_upd_step2))
                NumberedStep(stringResource(R.string.manual_upd_step3))
                NumberedStep(stringResource(R.string.manual_upd_step4))
                NumberedStep(stringResource(R.string.manual_upd_step5))
                NumberedStep(stringResource(R.string.manual_upd_step6))
                ManualNote(stringResource(R.string.manual_upd_nota))
            }

            // ── Consejos y problemas frecuentes ──
            SectionCard(
                title = stringResource(R.string.manual_ayuda),
                icon = Icons.Outlined.Info,
                color = MaterialTheme.colorScheme.tertiary
            ) {
                BulletPoint(stringResource(R.string.manual_ayuda_b1))
                BulletPoint(stringResource(R.string.manual_ayuda_b2))
                BulletPoint(stringResource(R.string.manual_ayuda_b3))
                BulletPoint(stringResource(R.string.manual_ayuda_b4))
                BulletPoint(stringResource(R.string.manual_ayuda_b5))
                BulletPoint(stringResource(R.string.manual_ayuda_b6))
                BulletPoint(stringResource(R.string.manual_ayuda_b7))
                BulletPoint(stringResource(R.string.manual_ayuda_b8))
            }

            Text(
                text = stringResource(R.string.manual_pie, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── Componentes internos ──────────────────────────────────

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    color: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SubTitle(text: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(2.dp))
}

/** Recuadro de apoyo: atajos, advertencias o explicaciones de jerga. */
@Composable
private fun ManualNote(text: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NumberedStep(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BulletPoint(text: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = "–",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ChainColorRow(name: String, color: Color, softColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(softColor),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ManualScreenPreview() {
    if (BuildConfig.DEBUG) {
        com.rutamercaderistas.ui.theme.MercaderistasTheme {
            ManualScreen(onClose = {})
        }
    }
}
