package com.sfj3861.rom2apk

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sfj3861.rom2apk.core.rom.ImportedRom
import com.sfj3861.rom2apk.core.rom.RomFormat
import com.sfj3861.rom2apk.core.rom.LibraryRom
import com.sfj3861.rom2apk.core.rom.RomLibraryStore
import com.sfj3861.rom2apk.core.rom.RomAnalyzer
import com.sfj3861.rom2apk.core.rom.RomAnalysis
import com.sfj3861.rom2apk.core.rom.RomCheckStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class ExportMode(val title: String, val subtitle: String) {
    SINGLE("APK individual", "Una aplicación independiente por juego"),
    LIBRARY("Biblioteca", "Varios juegos dentro de una aplicación")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Rom2ApkApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Rom2ApkApp() {
    val context = LocalContext.current
    var importedRom by remember { mutableStateOf<ImportedRom?>(null) }
    var importedUri by remember { mutableStateOf<Uri?>(null) }
    var analysis by remember { mutableStateOf<RomAnalysis?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var exportMode by remember { mutableStateOf(ExportMode.SINGLE) }
    var library by remember { mutableStateOf(RomLibraryStore.load(context)) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val resolver = context.contentResolver
        val fileName = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "archivo"

        val format = RomFormat.fromFileName(fileName)
        if (format == null) {
            importedRom = null
            importedUri = null
            analysis = null
            errorMessage = "Formato no reconocido. Revisa la extensión del archivo."
            return@rememberLauncherForActivityResult
        }

        val size = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) {
                if (cursor.isNull(index)) null else cursor.getLong(index)
            } else null
        }
        importedUri = uri
        analysis = null
        importedRom = ImportedRom(
            displayName = fileName.substringBeforeLast('.', fileName),
            fileName = fileName,
            mimeType = resolver.getType(uri),
            sizeBytes = size,
            format = format
        )
        errorMessage = null
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = if (isDark) darkColorScheme(
        primary = Color(0xFF9BB8FF),
        secondary = Color(0xFFB7C8E8),
        surface = Color(0xFF111318),
        background = Color(0xFF111318)
    ) else lightColorScheme(
        primary = Color(0xFF315DA8),
        secondary = Color(0xFF526783),
        surface = Color(0xFFFAF9FF),
        background = Color(0xFFF7F8FC)
    )

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Games, contentDescription = null)
                            Text(
                                "ROM2APK",
                                modifier = Modifier.padding(start = 9.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        androidx.compose.material3.IconButton(onClick = { }) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                WelcomeHeader()
                ImportCard(
                    importedRom = importedRom,
                    errorMessage = errorMessage,
                    onPick = {
                        picker.launch(
                            arrayOf(
                                "application/octet-stream",
                                "application/x-nes-rom",
                                "*/*"
                            )
                        )
                    },
                    onClear = {
                        importedRom = null
                        importedUri = null
                        analysis = null
                        errorMessage = null
                    },
                    onAnalyze = {
                        val selectedRom = importedRom
                        val selectedUri = importedUri
                        if (selectedRom != null && selectedUri != null && !isAnalyzing) {
                            isAnalyzing = true
                            analysis = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val stream = context.contentResolver.openInputStream(selectedUri)
                                            ?: error("Android no pudo abrir el archivo seleccionado.")
                                        RomAnalyzer.analyze(selectedRom.format, stream, selectedRom.sizeBytes)
                                    }.getOrElse {
                                        RomAnalysis(
                                            RomCheckStatus.INVALID,
                                            "No se pudo analizar",
                                            it.message ?: "Error de lectura del archivo."
                                        )
                                    }
                                }
                                analysis = result
                                isAnalyzing = false
                            }
                        }
                    },
                    isAnalyzing = isAnalyzing,
                    analysis = analysis,
                    onAddToLibrary = {
                        val rom = importedRom
                        val uri = importedUri
                        if (rom != null && uri != null) {
                            RomLibraryStore.add(context, LibraryRom(uri.toString(), rom.displayName, rom.fileName, rom.sizeBytes, rom.format))
                            library = RomLibraryStore.load(context)
                        }
                    },
                    isInLibrary = importedUri?.toString()?.let { selected -> library.any { it.uri == selected } } == true
                )
                LibraryCard(
                    entries = library,
                    onRemove = { entry ->
                        RomLibraryStore.remove(context, entry.uri)
                        runCatching { context.contentResolver.releasePersistableUriPermission(uriFromString(entry.uri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        library = RomLibraryStore.load(context)
                    }
                )
                ExportModeCard(
                    selected = exportMode,
                    onSelect = { exportMode = it }
                )
                BuildStatusCard(hasRom = importedRom != null, mode = exportMode)
                SupportedFormatsCard()
                Text(
                    "ROM2APK  •  Versión 0.1.0 · Base de desarrollo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .padding(bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun WelcomeHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Tus juegos, tus APK.", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Text(
            "Importa un archivo de juego y prepara una aplicación Android a tu manera.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ImportCard(
    importedRom: ImportedRom?,
    errorMessage: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
    onAnalyze: () -> Unit,
    isAnalyzing: Boolean,
    analysis: RomAnalysis?,
    onAddToLibrary: () -> Unit,
    isInLibrary: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Text("  Archivo del juego", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            if (importedRom == null) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            RoundedCornerShape(18.dp))
                        .padding(vertical = 26.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null,
                            modifier = Modifier.size(34.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Text("Todavía no has seleccionado ningún archivo",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("NES · SNES · GB · GBC · GBA · DS · 3DS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Text("  Seleccionar archivo")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            modifier = Modifier.padding(10.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(importedRom.displayName, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(importedRom.format.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(importedRom.sizeBytes?.let(::formatBytes) ?: "Tamaño no disponible",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                        Text("Quitar")
                    }
                    Button(onClick = onPick, modifier = Modifier.weight(1f)) {
                        Text("Cambiar")
                    }
                }
                Text("Formato detectado: .${importedRom.format.extension.uppercase(Locale.ROOT)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = onAddToLibrary, enabled = !isInLibrary, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.LibraryBooks, contentDescription = null)
                    Text(if (isInLibrary) "Guardado en la biblioteca" else "Añadir a la biblioteca")
                }
                Button(
                    onClick = onAnalyze,
                    enabled = !isAnalyzing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isAnalyzing) "Analizando archivo…" else "Analizar contenido")
                }
                if (analysis != null) {
                    val statusColor = when (analysis.status) {
                        RomCheckStatus.VALIDATED -> Color(0xFF23864A)
                        RomCheckStatus.NEEDS_SPECIAL_HANDLING -> MaterialTheme.colorScheme.primary
                        RomCheckStatus.INCONCLUSIVE -> Color(0xFFB7791F)
                        RomCheckStatus.INVALID -> MaterialTheme.colorScheme.error
                    }
                    Surface(
                        color = statusColor.copy(alpha = 0.09f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(analysis.title, fontWeight = FontWeight.SemiBold, color = statusColor)
                            Text("Código: ${analysis.diagnosticCode}", style = MaterialTheme.typography.labelSmall, color = statusColor)
                            Text(analysis.details, style = MaterialTheme.typography.bodySmall)
                            Text("Siguiente paso: ${analysis.recommendation}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "La validación estructural no garantiza que el juego sea ejecutable.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LibraryCard(entries: List<LibraryRom>, onRemove: (LibraryRom) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LibraryBooks, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("  Mi biblioteca", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(entries.size.toString(), style = MaterialTheme.typography.labelMedium)
            }
            if (entries.isEmpty()) {
                Text("Los juegos que añadas aparecerán aquí. Se guarda la referencia al archivo, no se duplica la ROM.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                entries.forEachIndexed { index, entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.displayName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${entry.format.extension.uppercase(Locale.ROOT)} · ${entry.sizeBytes?.let(::formatBytes) ?: "Tamaño desconocido"}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { onRemove(entry) }) { Text("Quitar") }
                    }
                    if (index != entries.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private fun uriFromString(value: String): Uri = Uri.parse(value)

@Composable
private fun ExportModeCard(selected: ExportMode, onSelect: (ExportMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("¿Cómo quieres exportar?", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        ExportMode.entries.forEach { mode ->
            Card(
                onClick = { onSelect(mode) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected == mode)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                ),
                border = if (selected == mode)
                    androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                else null
            ) {
                Row(modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (mode == ExportMode.SINGLE) Icons.Default.Extension
                        else Icons.Default.LibraryBooks,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(mode.title, fontWeight = FontWeight.SemiBold)
                        Text(mode.subtitle + if (mode == ExportMode.SINGLE) " · Generador pendiente" else " · Gestor de biblioteca pendiente", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilterChip(
                        selected = selected == mode,
                        onClick = { onSelect(mode) },
                        label = { Text(if (selected == mode) "Elegido" else "Elegir") }
                    )
                }
            }
        }
    }
}

@Composable
private fun BuildStatusCard(hasRom: Boolean, mode: ExportMode) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Text("Estado del proyecto", modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.SemiBold)
            }
            Text(
                if (hasRom) "Archivo importado. Puedes analizar su estructura; la generación de APK todavía no está disponible."
                else "Importa una ROM para analizarla. La exportación real se habilitará cuando el motor y el empaquetador estén integrados.",
                style = MaterialTheme.typography.bodyMedium
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Modalidad: ${mode.title}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Text("La extensión identifica el formato esperado. El analizador realiza comprobaciones estructurales preliminares; no certifica que el juego funcione.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SupportedFormatsCard() {
    val formats = listOf(
        "NES" to "NES",
        "SNES" to "SFC · SMC",
        "Game Boy" to "GB · GBC",
        "Game Boy Advance" to "GBA",
        "Nintendo DS" to "DS",
        "Nintendo 3DS" to "3DS · 3DSX · CIA"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Formatos previstos", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            formats.forEachIndexed { index, item ->
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.first, style = MaterialTheme.typography.bodyMedium)
                    Text(item.second, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (index != formats.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024.0} KB".format(Locale.ROOT)
    else -> "${bytes / (1024.0 * 1024.0)} MB".format(Locale.ROOT)
}
