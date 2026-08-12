package com.kardia.app.ui.generator

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kardia.app.ui.draft.FlashcardDraftEditorScreen

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GeneratorScreen(
    viewModel: GeneratorViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAnkiDroidAvailable = remember { viewModel.isAnkiDroidAvailable() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = uiState,
            transitionSpec = {
                fadeIn() with fadeOut()
            },
            label = "StateTransition"
        ) { state ->
            when (state) {
                is GeneratorState.ModelNotDownloaded -> {
                    ModelDownloadLayout(
                        state = state,
                        onDownloadClick = { url -> viewModel.downloadModel(url) }
                    )
                }
                is GeneratorState.Idle -> {
                    IdleGeneratorLayout(
                        state = state,
                        onGenerateClick = { notes, deckName ->
                            viewModel.generateFlashcards(notes, deckName)
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
    }
}

/**
 * 1. PANTALLA DE DESCARGA OTA (Modelo no disponible)
 */
@Composable
fun ModelDownloadLayout(
    state: GeneratorState.ModelNotDownloaded,
    onDownloadClick: (String) -> Unit
) {
    var modelUrl by remember {
        mutableStateOf("https://huggingface.co/google/gemma-3-1b-it-tflite/resolve/main/gemma-3-1b-it-q4.task")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudDownload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Requiere Modelo de IA Local",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Para garantizar la privacidad LOPD y el costo de mantenimiento de $0, Kardia ejecuta el LLM Gemma-3 1B IT localmente en tu teléfono (pesa ~1.5 GB).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Explicación premium de error 401 e instrucciones de alojamiento público
        androidx.compose.material3.Card(
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "💡 Solución al error 401 (Acceso Protegido)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "El repositorio oficial de Google en Hugging Face está protegido por licencia (gated) y da error 401. Para descargarlo de forma directa e ilimitada de por vida, sube el archivo a un sitio público:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Opción A: GitHub Releases (Recomendado)\nSube el archivo '.task' como un asset en los Releases de tu repositorio Kardia. Tendrás descarga directa rápida y gratis.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { modelUrl = "https://github.com/Imanolpc/Kardia/releases/download/v1.0.0/gemma-3-1b-it-q4.task" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Usar plantilla de enlace GitHub", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Opción B: Dropbox o OneDrive\nSube el archivo a Dropbox y comparte un enlace público. Reemplaza el final 'dl=0' por 'dl=1' para habilitar descarga directa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = modelUrl,
            onValueChange = { modelUrl = it },
            label = { Text("URL de Descarga del Modelo (.task)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isDownloading
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (state.isDownloading) {
            LinearProgressIndicator(
                progress = { state.downloadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.downloadMessage,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        } else {
            Button(
                onClick = { onDownloadClick(modelUrl) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Descargar Modelo OTA (1.5 GB)")
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
    onGenerateClick: (String, String) -> Unit
) {
    var notesText by remember { mutableStateOf(state.notesInput) }
    var deckName by remember { mutableStateOf(state.deckName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Kardia Local AI",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        brush = Brush.horizontalGradient(
                            colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                        )
                    )
                )
            }
            Text(
                text = "Generador de tarjetas compatible con Anki",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = deckName,
                onValueChange = { deckName = it },
                label = { Text("Nombre del Mazo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text("Apuntes / Textos para Tarjetas") },
                placeholder = { Text("Pega aquí apuntes médicos, fórmulas, historia, o cualquier texto del cual desees generar tarjetas de repetición espaciada...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                maxLines = 15
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

        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onGenerateClick(notesText, deckName) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generar Tarjetas de Estudio", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 3. PANTALLA DE CARGA REACTIVA Y PREVENCIÓN LMK (Con visualizador de anuncios)
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
        // Bloque Superior: Animación e Identidad de la App
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
            
            // LinearProgressIndicator reactivo en tiempo real
            LinearProgressIndicator(
                progress = state.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Color.Transparent, shape = RoundedCornerShape(4.dp)),
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
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Vista parcial en streaming de los tokens generados por LiteRT-LM (opcional)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Box(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        text = if (state.partialText.isBlank()) "Esperando respuesta de Gemma-3..." else state.partialText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Bloque Central: Banner de Advertencia de Hardware Obligatorio (Peligro LMK)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFF9C4) // Amarillo vivo de alerta
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
                    tint = Color(0xFFF57C00), // Naranja alerta
                    modifier = Modifier
                        .size(36.dp)
                        .padding(end = 8.dp)
                )
                Column {
                    Text(
                        text = "¡IMPORTANTE! No minimices Kardia",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD84315)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Por privacidad, las tarjetas se procesan 100% de forma local en tu teléfono saturando la memoria (2GB+). Si minimizas la app, apagas la pantalla o abres otra aplicación, el Low Memory Killer (LMK) de Android detendrá y corromperá el proceso para liberar RAM.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.DarkGray,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Bloque Inferior: Contenedor de Anuncio Patrocinado (Monetización recurrente del costo $0)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Patrocinador - Tu apoyo financia nuestro desarrollo privado y local",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Simulación nativa visual del banner publicitario
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "[ Banner Publicitario - Anuncio AdMob ]",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Obtén la versión Premium para remover anuncios y acelerar la IA.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
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
                .height(50.dp)
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
                .height(50.dp)
        ) {
            Text("Volver a intentar")
        }
    }
}
