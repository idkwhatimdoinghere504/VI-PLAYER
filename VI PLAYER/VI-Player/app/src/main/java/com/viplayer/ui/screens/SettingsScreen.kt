package com.viplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.viplayer.data.AppSettings
import com.viplayer.data.SongSort
import com.viplayer.data.ThemeMode
import com.viplayer.playback.EqState
import com.viplayer.ui.theme.Accents

@Composable
fun SettingsScreen(
    settings: AppSettings,
    contentPadding: PaddingValues,
    sleepRemainingMs: Long,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (Int) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onSort: (SongSort) -> Unit,
    onSleepTimer: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item { SettingsHeader("Appearance") }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onThemeMode(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text(
                    text = "Accent colour",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Accents.forEachIndexed { index, accent ->
                        val selected = index == settings.accentIndex
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(accent.seed)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { onAccent(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = accent.name,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SwitchRow(
                title = "Dynamic colour",
                subtitle = "Match the system palette on Android 12 and newer",
                checked = settings.dynamicColor,
                onCheckedChange = onDynamicColor
            )
        }

        item { SettingsHeader("Library") }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Sort songs by",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                SongSort.entries.forEach { sort ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = settings.sort == sort,
                                onClick = { onSort(sort) }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = settings.sort == sort, onClick = { onSort(sort) })
                        Spacer(Modifier.width(4.dp))
                        Text(sort.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        item { SettingsHeader("Sleep timer") }

        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                if (sleepRemainingMs > 0L) {
                    val totalSeconds = sleepRemainingMs / 1000
                    val minutes = totalSeconds / 60
                    val seconds = totalSeconds % 60
                    Text(
                        text = "Pausing in %d:%02d".format(minutes, seconds),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { onSleepTimer(0) }) { Text("Cancel timer") }
                } else {
                    Text(
                        text = "Stop playback automatically",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 45, 60, 120).forEach { minutes ->
                            FilterChip(
                                selected = false,
                                onClick = { onSleepTimer(minutes) },
                                label = { Text("${minutes}m") }
                            )
                        }
                    }
                }
            }
        }

        item { SettingsHeader("About") }

        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = "VI Player",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "A local music player built with Kotlin and Jetpack Compose. " +
                        "It plays audio the system has indexed on your device and never " +
                        "uploads anything.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
        item { Spacer(Modifier.height(contentPadding.calculateBottomPadding())) }
    }
}

@Composable
private fun SettingsHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 10.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    state: EqState,
    contentPadding: PaddingValues,
    onToggle: (Boolean) -> Unit,
    onBandLevel: (Int, Int) -> Unit,
    onPreset: (Int) -> Unit,
    onFlatten: () -> Unit
) {
    if (!state.available) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Equalizer unavailable",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "This device did not expose a system audio effect for the current " +
                    "playback session. Start a track and try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            SwitchRow(
                title = "Enable equalizer",
                subtitle = "Applies to the current playback session",
                checked = state.enabled,
                onCheckedChange = onToggle
            )
        }

        if (state.presets.isNotEmpty()) {
            item { SettingsHeader("Presets") }
            item {
                LazyColumn(
                    modifier = Modifier.height(200.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(state.presets.withIndex().toList(), key = { it.index }) { indexed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = indexed.index == state.presetIndex,
                                    onClick = { onPreset(indexed.index) }
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = indexed.index == state.presetIndex,
                                onClick = { onPreset(indexed.index) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = indexed.value,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }

        item { SettingsHeader("Bands") }

        items(state.bands, key = { it.index }) { band ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatHz(band.centerHz),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = formatDb(band.levelMb),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = band.levelMb.toFloat(),
                    onValueChange = { onBandLevel(band.index, it.toInt()) },
                    valueRange = band.minMb.toFloat()..band.maxMb.toFloat(),
                    enabled = state.enabled
                )
            }
        }

        item {
            TextButton(
                onClick = onFlatten,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                enabled = state.enabled
            ) { Text("Reset to flat") }
        }

        item { Spacer(Modifier.height(24.dp)) }
        item { Spacer(Modifier.height(contentPadding.calculateBottomPadding())) }
    }
}

private fun formatHz(hz: Int): String =
    if (hz >= 1000) "%.1f kHz".format(hz / 1000f) else "$hz Hz"

private fun formatDb(mb: Int): String = "%+.1f dB".format(mb / 100f)
