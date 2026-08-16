package imanolpc.kardia.ui.draft

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import imanolpc.kardia.core.anki.DraftCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardDraftEditorScreen(
    deckName: String,
    drafts: List<DraftCard>,
    isAnkiDroidAvailable: Boolean,
    onSaveToKardia: (List<DraftCard>) -> Unit,
    onSaveToCollection: (List<DraftCard>) -> Unit,
    onImportToAnkiDroid: (List<DraftCard>) -> Unit,
    onBack: () -> Unit
) {
    var editableDrafts by remember { mutableStateOf(drafts) }
    var activeInfoCardIndex by remember { mutableStateOf<Int?>(null) }

    activeInfoCardIndex?.let { index ->
        val card = editableDrafts.getOrNull(index)
        if (card != null && card.sourceText.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { activeInfoCardIndex = null },
                title = { Text("Párrafo de Origen") },
                text = {
                    Text(
                        text = card.sourceText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = { activeInfoCardIndex = null }) {
                        Text("Entendido")
                    }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Borrador: $deckName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Filtro de Control de Calidad (${editableDrafts.size} tarjetas)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Revisa y ajusta cualquier tarjeta antes de guardarla en tu biblioteca.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                itemsIndexed(editableDrafts) { index, card ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Tarjeta #${index + 1}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (card.sourceText.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = { activeInfoCardIndex = index },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = "Ver párrafo original",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        editableDrafts = editableDrafts.toMutableList().apply {
                                            removeAt(index)
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Eliminar Tarjeta",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedTextField(
                                value = card.front,
                                onValueChange = { newFront ->
                                    editableDrafts = editableDrafts.toMutableList().apply {
                                        this[index] = card.copy(front = newFront)
                                    }
                                },
                                label = { Text("Anverso (Pregunta)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                maxLines = 4
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = card.back,
                                onValueChange = { newBack ->
                                    editableDrafts = editableDrafts.toMutableList().apply {
                                        this[index] = card.copy(back = newBack)
                                    }
                                },
                                label = { Text("Reverso (Respuesta)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                maxLines = 6
                            )
                        }
                    }
                }
            }

            // Acciones: Guardar en Kardia (Principal) + Opciones de Anki
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // BOTÓN PRINCIPAL: Guardar en Kardia
                    Button(
                        onClick = { onSaveToKardia(editableDrafts) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = editableDrafts.isNotEmpty()
                    ) {
                        Icon(Icons.Default.BookmarkAdded, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardar en Biblioteca Kardia", fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSaveToCollection(editableDrafts) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            enabled = editableDrafts.isNotEmpty()
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Exportar .apkg", maxLines = 1)
                        }

                        if (isAnkiDroidAvailable) {
                            OutlinedButton(
                                onClick = { onImportToAnkiDroid(editableDrafts) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                enabled = editableDrafts.isNotEmpty()
                            ) {
                                Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("AnkiDroid", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
