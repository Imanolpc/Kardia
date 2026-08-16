package imanolpc.kardia.ui.generator

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Tune
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import imanolpc.kardia.core.ai.AIModelInfo
import imanolpc.kardia.ui.draft.FlashcardDraftEditorScreen
import imanolpc.kardia.ui.settings.ModelSettingsDialog

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GeneratorScreen(
    viewModel: GeneratorViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAnkiDroidAvailable = remember { viewModel.isAnkiDroidAvailable() }

    val availableModels by viewModel.availableModels.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val downloadingModelId by viewModel.downloadingModelId.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadMessage by viewModel.downloadMessage.collectAsState()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = uiState,
            contentKey = { state -> state::class },
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "StateTransition"
        ) { state ->
            when (state) {
                is GeneratorState.ModelNotDownloaded -> {
                    ModelDownloadLayout(
                        state = state,
                        selectedModel = selectedModel,
                        onDownloadClick = { model -> viewModel.downloadModel(model) },
                        onOpenSettings = { viewModel.openSettings() }
                    )
                }
                is GeneratorState.Idle -> {
                    IdleGeneratorLayout(
                        state = state,
                        activeModel = selectedModel,
                        onOpenSettings = { viewModel.openSettings() },
                        onGenerateClick = { notes, deckName ->
                            viewModel.generateFlashcards(notes, deckName)
                        },
                        onDocumentSelected = { uri, onExtracted ->
                            viewModel.extractTextFromDocument(uri, onExtracted)
                        }
                    )
                }
                is GeneratorState.Generating -> {
                    GeneratingLayout(state = state)
                }
                is GeneratorState.Drafting -> {
                    FlashcardDraftEditorScreen(
                        deckName = state.deckName,
                        drafts = state.drafts,
                        isAnkiDroidAvailable = isAnkiDroidAvailable,
                        onSaveToCollection = { finalDrafts ->
                            viewModel.exportToApkg(state.deckName, finalDrafts)
                        },
                        onImportToAnkiDroid = { finalDrafts ->
                            viewModel.importToAnkiDroid(state.deckName, finalDrafts)
                        },
                        onBack = { viewModel.resetToIdle() }
                    )
                }
                is GeneratorState.Success -> {
                    SuccessLayout(
                        state = state,
                        onNewDeckClick = { viewModel.resetToIdle() }
                    )
                }
                is GeneratorState.Error -> {
                    ErrorLayout(
                        state = state,
                        onRetryClick = { viewModel.resetToIdle() }
                    )
                }
            }
        }

        // Diálogo de configuración de modelos
        if (isSettingsOpen) {
            ModelSettingsDialog(
                models = availableModels,
                selectedModel = selectedModel,
                downloadingModelId = downloadingModelId,
                downloadProgress = downloadProgress,
                downloadMessage = downloadMessage,
                isModelDownloaded = { model -> viewModel.llmManager.isModelDownloaded(model) },
                onSelectModel = { model -> viewModel.selectModel(model) },
                onDownloadModel = { model -> viewModel.downloadModel(model) },
                onDeleteModel = { model -> viewModel.deleteModel(model) },
                onImportLocalFile = { uri, name -> viewModel.importLocalModel(uri, name) },
                onRefreshCatalog = { viewModel.loadCatalogAndCheckModel() },
                onDismissRequest = { viewModel.closeSettings() }
            )
        }
    }
}

/**
 * 1. PANTALLA DE DESCARGA OTA (Modelo no disponible)
 */
@Composable
fun ModelDownloadLayout(
    state: GeneratorState.ModelNotDownloaded,
    selectedModel: AIModelInfo?,
    onDownloadClick: (AIModelInfo) -> Unit,
    onOpenSettings: () -> Unit
) {
    val targetModel = state.selectedModel ?: selectedModel ?: AIModelInfo.DEFAULT_CATALOG.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configuración de modelos",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Icon(
            imageVector = Icons.Default.CloudDownload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Configuración del Modelo IA",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Kardia procesa tus notas de forma 100% local y privada sin servidores externos. Elige o descarga el modelo que prefieras.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Tarjeta del modelo a descargar
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = targetModel.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = targetModel.formattedSize,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = targetModel.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onOpenSettings) {
            Icon(imageVector = Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Ver todos los modelos disponibles / Importar archivo")
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (state.isDownloading) {
            LinearProgressIndicator(
                progress = { state.downloadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.downloadMessage,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        } else {
            Button(
                onClick = { onDownloadClick(targetModel) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Descargar ${targetModel.name}")
            }
        }

        state.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 2. PANTALLA PRINCIPAL DE ENTRADA (Mazo e inputs de apuntes)
 */
@Composable
fun IdleGeneratorLayout(
    state: GeneratorState.Idle,
    activeModel: AIModelInfo?,
    onOpenSettings: () -> Unit,
    onGenerateClick: (String, String) -> Unit,
    onDocumentSelected: (Uri, (String) -> Unit) -> Unit
) {
    var notesText by remember { mutableStateOf(state.notesInput) }
    var deckName by remember { mutableStateOf(state.deckName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // Header con Brand y Botón de Ajustes de Modelos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kardia AI",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            brush = Brush.horizontalGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                            )
                        )
                    )
                }

                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configuración de modelos",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Chip indicador del modelo activo y optimización de memoria
            if (activeModel != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpenSettings() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = activeModel.name,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        Text(
                            text = "Cambiar ⚙️",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = deckName,
                onValueChange = { deckName = it },
                label = { Text("Nombre del Mazo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            var isParsing by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Apuntes / Material de estudio",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                val fileLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    uri?.let {
                        isParsing = true
                        onDocumentSelected(it) { extractedText ->
                            notesText = extractedText
                            isParsing = false
                        }
                    }
                }

                TextButton(
                    onClick = { fileLauncher.launch(arrayOf("application/pdf", "text/plain")) },
                    enabled = !isParsing
                ) {
                    if (isParsing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Leyendo...", style = MaterialTheme.typography.labelSmall)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cargar PDF / TXT", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                placeholder = { Text("Pega tus apuntes aquí o carga un archivo PDF/TXT...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 15,
                trailingIcon = {
                    if (notesText.isNotEmpty()) {
                        IconButton(onClick = { notesText = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Limpiar texto",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )

            state.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Button(
                onClick = { onGenerateClick(notesText, deckName) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generar Tarjetas de Estudio", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * 3. PANTALLA DE CARGA REACTIVA Y PREVENCIÓN LMK
 */
@Composable
fun GeneratingLayout(state: GeneratorState.Generating) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Generando Tarjetas con IA",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = state.subtaskDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFF9C4)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp)) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Alerta de Hardware",
                    tint = Color(0xFFF57C00),
                    modifier = Modifier
                        .size(36.dp)
                        .padding(end = 8.dp)
                )
                Column {
                    Text(
                        text = "¡IMPORTANTE! No salgas de Kardia",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD84315)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "El modelo está procesando de forma 100% local en tu memoria. Una vez termine la generación, la memoria RAM se liberará automáticamente al 100%.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.DarkGray,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Placeholder para Publicidad / Banner
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Patrocinador",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "[ Anuncio Publicitario ]",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 4. PANTALLA DE ÉXITO
 */
@Composable
fun SuccessLayout(
    state: GeneratorState.Success,
    onNewDeckClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = state.message,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onNewDeckClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Generar otro mazo")
        }
    }
}

/**
 * 5. PANTALLA DE ERROR
 */
@Composable
fun ErrorLayout(
    state: GeneratorState.Error,
    onRetryClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Ha ocurrido un error",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRetryClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Volver a intentar")
        }
    }
}
