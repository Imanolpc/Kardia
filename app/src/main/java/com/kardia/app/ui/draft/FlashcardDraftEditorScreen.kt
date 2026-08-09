package com.kardia.app.ui.draft

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.kardia.app.core.anki.DraftCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardDraftEditorScreen(
    deckName: String,
    drafts: List<DraftCard>,
    isAnkiDroidAvailable: Boolean,
    onSaveToCollection: (List<DraftCard>) -> Unit,
    onImportToAnkiDroid: (List<DraftCard>) -> Unit,
    onBack: () -> Unit
) {
    // Mantener estado mutable local para la edición
    var editableDrafts by remember { mutableStateOf(drafts) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Borrador de IA: $deckName") },
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
            // Cabecera instructiva de calidad contra alucinaciones
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Filtro de Control de Calidad",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Las IAs locales pueden alucinar u omitir detalles. Modifica o elimina cualquier campo antes de exportar permanentemente el mazo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(editableDrafts) { index, card ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
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
                                Text(
                                    text = "Tarjeta #${index + 1}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = {
                                        editableDrafts = editableDrafts.toMutableList().apply {
                                            removeAt(index)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Eliminar Tarjeta",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            
                            OutlinedTextField(
                                value = card.front,
                                onValueChange = { newFront ->
                                    editableDrafts = editableDrafts.toMutableList().apply {
                                        this[index] = card.copy(front = newFront)
                                    }
                                },
                                label = { Text("Anverso (Pregunta / Concepto)") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 4
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = card.back,
                                onValueChange = { newBack ->
                                    editableDrafts = editableDrafts.toMutableList().apply {
                                        this[index] = card.copy(back = newBack)
                                    }
                                },
                                label = { Text("Reverso (Respuesta / Definición)") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 6
                            )
                        }
                    }
                }
            }

            // Barra inferior con acciones de exportación nativas
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isAnkiDroidAvailable) {
                        Button(
                            onClick = { onImportToAnkiDroid(editableDrafts) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            enabled = editableDrafts.isNotEmpty()
                        ) {
                            Icon(Icons.Default.SystemUpdateAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Inyectar en AnkiDroid (ContentProvider)")
                        }
                    }

                    OutlinedButton(
                        onClick = { onSaveToCollection(editableDrafts) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editableDrafts.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exportar como archivo .apkg (Offline)")
                    }
                }
            }
        }
    }
}
