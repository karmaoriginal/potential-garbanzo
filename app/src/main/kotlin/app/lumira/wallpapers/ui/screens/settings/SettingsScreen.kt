package app.lumira.wallpapers.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lumira.wallpapers.data.prefs.Settings
import app.lumira.wallpapers.domain.model.QualityPreset

/**
 * Settings: appearance (Material You / AMOLED), live wallpaper FPS and battery rules,
 * video quality, cache size and Wi-Fi-only downloads.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 44.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                }
                Text("Ajustes", style = MaterialTheme.typography.headlineSmall)
            }
        }

        item { SectionTitle("Apariencia") }
        item {
            SettingCard {
                SwitchRow(
                    title = "Color dinámico (Material You)",
                    subtitle = "Usa los colores de tu fondo de pantalla",
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
                SwitchRow(
                    title = "Modo AMOLED",
                    subtitle = "Negro puro en superficies oscuras",
                    checked = settings.amoled,
                    onCheckedChange = viewModel::setAmoled,
                )
                Text("Columnas en la biblioteca: ${settings.gridColumns}", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.gridColumns.toFloat(),
                    onValueChange = { viewModel.setGridColumns(it.toInt()) },
                    valueRange = 2f..4f,
                    steps = 1,
                )
            }
        }

        item { SectionTitle("Wallpaper animado") }
        item {
            SettingCard {
                Text("Límite de FPS", style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    listOf(30, 60).forEachIndexed { index, fps ->
                        SegmentedButton(
                            selected = settings.liveWallpaperFps == fps,
                            onClick = { viewModel.setLiveFps(fps) },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                        ) { Text("$fps fps") }
                    }
                }
                SwitchRow(
                    title = "Silenciar videos",
                    subtitle = "Recomendado para ahorrar batería",
                    checked = settings.liveWallpaperMuted,
                    onCheckedChange = viewModel::setLiveMuted,
                )
                SwitchRow(
                    title = "Pausar con la pantalla apagada",
                    subtitle = "Nunca decodifica en segundo plano",
                    checked = settings.liveWallpaperPauseOffScreen,
                    onCheckedChange = viewModel::setPauseOffScreen,
                )
                SwitchRow(
                    title = "Pausar con ahorro de batería",
                    subtitle = "Se detiene si el sistema entra en modo ahorro",
                    checked = settings.liveWallpaperBatterySaverPause,
                    onCheckedChange = viewModel::setPauseBattery,
                )
            }
        }

        item { SectionTitle("Calidad de video") }
        item {
            SettingCard {
                Text(
                    "Resolución objetivo al mejorar",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    val options = listOf(QualityPreset.HD_1080, QualityPreset.QHD_1440)
                    options.forEachIndexed { index, preset ->
                        SegmentedButton(
                            selected = settings.qualityPreset == preset,
                            onClick = { viewModel.setQuality(preset) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) { Text(preset.label) }
                    }
                }
                SwitchRow(
                    title = "Mejorar automáticamente al importar",
                    subtitle = "Solo videos de baja resolución, al cargar el teléfono",
                    checked = settings.autoEnhance,
                    onCheckedChange = viewModel::setAutoEnhance,
                )
                SwitchRow(
                    title = "Conservar el video original",
                    subtitle = "Ocupa más espacio pero puedes revertir",
                    checked = settings.keepOriginal,
                    onCheckedChange = viewModel::setKeepOriginal,
                )
                SwitchRow(
                    title = "Upscaler con IA (experimental)",
                    subtitle = "Requiere un modelo ligero; desactivado por defecto",
                    checked = settings.aiUpscale,
                    onCheckedChange = viewModel::setAiUpscale,
                )
            }
        }

        item { SectionTitle("Red y almacenamiento") }
        item {
            SettingCard {
                SwitchRow(
                    title = "Descargas solo por Wi-Fi",
                    subtitle = "Evita gastar datos móviles",
                    checked = settings.wifiOnly,
                    onCheckedChange = viewModel::setWifiOnly,
                )
                SwitchRow(
                    title = "Limpiar caché automáticamente",
                    subtitle = "Borra miniaturas y caché de imágenes antiguas",
                    checked = settings.autoCleanCache,
                    onCheckedChange = viewModel::setAutoClean,
                )
                Text("Límite de caché: ${settings.cacheLimitMb} MB", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.cacheLimitMb.toFloat(),
                    onValueChange = { viewModel.setCacheLimit(it.toInt()) },
                    valueRange = 64f..1024f,
                    steps = 14,
                )
            }
        }

        item {
            Text(
                "Lumira 1.0.0 · Contenido de la tienda con licencias libres (CC0 / Pexels / Pixabay). " +
                    "Hecho con Jetpack Compose, Media3 y FFmpeg.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), content = content)
    }
}

private typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Suppress("unused")
private val settingsTypeAnchor: Settings = Settings()
