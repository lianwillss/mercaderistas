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
import androidx.compose.ui.res.stringResource
import com.rutamercaderistas.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.ui.theme.AccentBlue
import com.rutamercaderistas.ui.theme.AccentBlueSoft
import com.rutamercaderistas.ui.theme.AccentGreen
import com.rutamercaderistas.ui.theme.AccentGreenSoft
import com.rutamercaderistas.ui.theme.AccentOrange
import com.rutamercaderistas.ui.theme.StoreColorFuchsia
import com.rutamercaderistas.ui.theme.StoreColorFuchsiaSoft
import com.rutamercaderistas.ui.theme.StoreColorPurple
import com.rutamercaderistas.ui.theme.StoreColorPurpleSoft
import com.rutamercaderistas.ui.theme.StoreColorRed
import com.rutamercaderistas.ui.theme.StoreColorRedSoft
import com.rutamercaderistas.ui.theme.StoreColorYellow
import com.rutamercaderistas.ui.theme.StoreColorYellowSoft

@Composable
fun ManualScreen(
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.volver_cd),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(R.string.manual_usuario_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Qué es Mercaderistas ──
            SectionCard(
                title = stringResource(R.string.manual_que_es),
                icon = Icons.Outlined.Info,
                color = AccentBlue
            ) {
                    Text(
                        text = "Mercaderistas es una aplicación que te permite gestionar tus rutas de " +
                                "visita a locales comerciales. Descarga automáticamente un Excel desde " +
                                "Google Drive con la información de cada ruta, organiza los locales por " +
                                "día y te permite abrir el PDF del catalogador en la página exacta de " +
                                "cada marca.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.manual_funciona_sin_conexion),
                        style = MaterialTheme.typography.titleSmall,
                        color = AccentBlue,
                    )
            }

            // ── Sincronizar datos ──
            SectionCard(
                title = stringResource(R.string.manual_sincronizar),
                icon = Icons.Outlined.Refresh,
                color = AccentGreen
            ) {
                NumberedStep("Toca el botón ⋮ (tres puntos) en la esquina superior derecha.")
                NumberedStep("Selecciona \"Forzar sincronización\".")
                NumberedStep("Una barra de progreso indica que los datos se están descargando.")
                NumberedStep("Al terminar, la lista de rutas se actualiza automáticamente.")
                Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.manual_check_datos),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }

            // ── Seleccionar una ruta ──
            SectionCard(
                title = stringResource(R.string.manual_seleccionar_ruta),
                icon = Icons.Outlined.Store,
                color = AccentOrange
            ) {
                NumberedStep("Toca el campo \"Buscar ruta…\" en la parte superior.")
                NumberedStep("Escribe el nombre de la ruta (ej. \"RUTA 1\").")
                NumberedStep("Selecciona la ruta de la lista desplegable.")
                NumberedStep("También puedes elegir una ruta reciente del historial.")
            }

            // ── Navegar entre días ──
            SectionCard(
                title = stringResource(R.string.manual_navegar_dias),
                icon = Icons.Outlined.Info,
                color = StoreColorPurple
            ) {
                Text(
                    text = "Usa el selector de días (LUN, MAR, MIÉ, etc.) para cambiar de día. " +
                            "También puedes deslizar horizontalmente sobre la pantalla.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.manual_sin_visitas_selector),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Colores de cadena ──
            SectionCard(
                title = stringResource(R.string.manual_colores_cadena),
                icon = Icons.Outlined.Store,
                color = AccentBlue
            ) {
                ChainColorRow("Jumbo", AccentGreen, AccentGreenSoft)
                ChainColorRow("Lider", AccentBlue, AccentBlueSoft)
                ChainColorRow("Santa Isabel / SISA", StoreColorFuchsia, StoreColorFuchsiaSoft)
                ChainColorRow("Unimarc", StoreColorRed, StoreColorRedSoft)
                ChainColorRow("Tottus", StoreColorYellow, StoreColorYellowSoft)
                ChainColorRow("Alvi", StoreColorPurple, StoreColorPurpleSoft)
            }

            // ── La tarjeta de local ──
            SectionCard(
                title = stringResource(R.string.manual_tarjeta_local),
                icon = Icons.Outlined.Store,
                color = AccentBlue
            ) {
                Text(
                    text = "Cada local se muestra como una tarjeta blanca con bordes redondeados " +
                            "y sombra sutil. Contiene:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(6.dp))
                BulletPoint("Icono con el color de la cadena (ver tabla arriba)")
                BulletPoint("Nombre del local en formato natural")
                BulletPoint("Código interno del local")
                BulletPoint("Dirección en azul (toca para abrir en Google Maps)")
                BulletPoint("Contador circular con el número de marcas a visitar")
            }

            // ── Marcas ──
            SectionCard(
                title = stringResource(R.string.manual_marcas),
                icon = Icons.Outlined.Description,
                color = AccentOrange
            ) {
                Text(
                    text = "Marcas prioritarias:",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                BulletPoint("Tarjeta blanca independiente con barra naranja a la izquierda")
                BulletPoint("Ícono de estrella dentro de un círculo naranja")
                BulletPoint("Chip verde que indica la frecuencia de visita (ej. \"3×\" semanal)")
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Marcas normales:",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                BulletPoint("Fila compacta sin tarjeta independiente")
                BulletPoint("Círculo con iniciales de la marca en un color generado automáticamente")
                BulletPoint("Badge de frecuencia cuando corresponde")
            }

            // ── Abrir PDF ──
            SectionCard(
                title = stringResource(R.string.manual_abrir_pdf),
                icon = Icons.Outlined.Description,
                color = StoreColorFuchsia
            ) {
                NumberedStep("Toca cualquier marca dentro de un local.")
                NumberedStep("La aplicación abre el PDF del catalogador en la página exacta de esa marca.")
                NumberedStep("Si la marca no se encuentra en el PDF, aparece un mensaje: \"Marca no encontrada\".")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.manual_navegacion_pdf),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Ver todos los locales ──
            SectionCard(
                title = stringResource(R.string.manual_ver_locales),
                icon = Icons.Outlined.LocationOn,
                color = AccentBlue
            ) {
                NumberedStep("Toca la tarjeta \"Locales\" en la sección de estadísticas (arriba, junto a Marcas y Visitas).")
                NumberedStep("Se abre una pantalla completa con todos los locales de la ruta.")
                NumberedStep("Cada local muestra su nombre, dirección y código de colores por cadena.")
                NumberedStep("Toca la dirección para abrirla en Google Maps.")
                NumberedStep("Presiona ← para volver.")
            }

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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
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
    com.rutamercaderistas.ui.theme.MercaderistasTheme {
        ManualScreen(onClose = {})
    }
}
