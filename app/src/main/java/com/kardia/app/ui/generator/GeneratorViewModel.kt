package com.kardia.app.ui.generator

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kardia.app.core.ai.LocalLLMManager
import com.kardia.app.core.anki.AnkiApkgCompiler
import com.kardia.app.core.anki.AnkiDroidConnector
import com.kardia.app.core.anki.DraftCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.net.Uri
import com.kardia.app.core.util.DocumentParser
import kotlinx.coroutines.withContext
import android.util.Log
import java.io.File
import java.util.UUID

class GeneratorViewModel(application: Application) : AndroidViewModel(application) {

    private val llmManager = LocalLLMManager(application)
    private val compiler = AnkiApkgCompiler(application)
    private val connector = AnkiDroidConnector(application)

    private val _uiState = MutableStateFlow<GeneratorState>(GeneratorState.ModelNotDownloaded())
    val uiState: StateFlow<GeneratorState> = _uiState.asStateFlow()

    init {
        checkModelPresence()
    }

    /**
     * Verifica si el modelo está disponible localmente para determinar el estado de arranque.
     */
    fun checkModelPresence() {
        viewModelScope.launch {
            if (llmManager.isModelDownloaded()) {
                _uiState.value = GeneratorState.Generating(0.1f, "Inicializando motor de inferencia local...")
                val initResult = llmManager.initializeModel()
                if (initResult.isSuccess) {
                    _uiState.value = GeneratorState.Idle()
                } else {
                    _uiState.value = GeneratorState.ModelNotDownloaded(
                        errorMessage = "Error de inicialización: ${initResult.exceptionOrNull()?.message}"
                    )
                }
            } else {
                _uiState.value = GeneratorState.ModelNotDownloaded(
                    downloadMessage = "El modelo Gemma-3 1B IT local no se encuentra en el dispositivo."
                )
            }
        }
    }

    /**
     * Descarga el modelo OTA de forma asíncrona y lo inicializa.
     */
    fun downloadModel(url: String) {
        val currentState = _uiState.value
        if (currentState is GeneratorState.ModelNotDownloaded && currentState.isDownloading) return

        _uiState.value = GeneratorState.ModelNotDownloaded(isDownloading = true, downloadMessage = "Iniciando descarga...")

        viewModelScope.launch {
            llmManager.downloadModel(url).collect { status ->
                when (status) {
                    is LocalLLMManager.DownloadStatus.Progress -> {
                        _uiState.value = GeneratorState.ModelNotDownloaded(
                            downloadProgress = status.progress,
                            downloadMessage = status.message,
                            isDownloading = true
                        )
                    }
                    is LocalLLMManager.DownloadStatus.Success -> {
                        _uiState.value = GeneratorState.Generating(0.1f, "Inicializando motor de inferencia local...")
                        val initResult = llmManager.initializeModel()
                        if (initResult.isSuccess) {
                            _uiState.value = GeneratorState.Idle()
                        } else {
                            _uiState.value = GeneratorState.ModelNotDownloaded(
                                errorMessage = "Error al iniciar modelo tras descarga: ${initResult.exceptionOrNull()?.message}"
                            )
                        }
                    }
                    is LocalLLMManager.DownloadStatus.Error -> {
                        _uiState.value = GeneratorState.ModelNotDownloaded(
                            errorMessage = "Fallo en la descarga: ${status.exception.message}",
                            isDownloading = false
                        )
                    }
            }
        }
    }
}

    /**
     * Extrae de forma asíncrona en IO el texto de un PDF o TXT.
     */
    fun extractTextFromDocument(uri: Uri, onTextExtracted: (String) -> Unit) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                DocumentParser.extractTextFromUri(getApplication(), uri)
            }
            onTextExtracted(text)
        }
    }

    /**
     * Genera tarjetas en base a los apuntes del usuario mediante LiteRT-LM.
     */
    fun generateFlashcards(notes: String, deckName: String) {
        if (notes.isBlank()) {
            _uiState.value = GeneratorState.Idle(notesInput = notes, deckName = deckName, errorMessage = "Los apuntes no pueden estar vacíos")
            return
        }

        _uiState.value = GeneratorState.Generating(0.15f, "Preparando el motor de IA...")

        viewModelScope.launch(Dispatchers.Default) {
            // Estructuramos el prompt con el template oficial de chat de Gemma
            val prompt = """
                <start_of_turn>user
                Eres un asistente de estudio experto. Analiza el siguiente texto y genera entre 3 y 6 tarjetas de estudio (flashcards) claras y concisas en español.
                Cada tarjeta consta de un anverso (pregunta o concepto) y un reverso (explicación o respuesta).
                Debes responder STRICTLY con el formato que se muestra a continuación, sin introducir otros textos ni explicaciones adicionales:

                Q: ¿Cuál es el concepto o pregunta?
                A: Explicación directa y resumida.
                ---
                Q: ¿Cuál es otro concepto?
                A: Otra explicación concisa.
                ---

                Texto a procesar:
                $notes<end_of_turn>
                <start_of_turn>model
            """.trimIndent()

            var fullResponse = ""
            var cardWritingProgress = 0.20f

            llmManager.generateFlashcardsStream(prompt).collect { status ->
                when (status) {
                    is LocalLLMManager.InferenceStatus.Starting -> {
                        _uiState.value = GeneratorState.Generating(
                            progress = 0.20f,
                            subtaskDescription = "Gemma-3 1B IT analizando texto..."
                        )
                    }
                    is LocalLLMManager.InferenceStatus.Token -> {
                        fullResponse += status.text
                        Log.d("KardiaAI", "Token: '${status.text}'")
                        // Simular progreso incremental durante la inferencia para mejorar la UX
                        if (cardWritingProgress < 0.80f) {
                            cardWritingProgress += 0.005f
                        }
                        _uiState.value = GeneratorState.Generating(
                            progress = cardWritingProgress,
                            subtaskDescription = "IA local redactando tarjetas de repetición...",
                            partialText = fullResponse
                        )
                    }
                    is LocalLLMManager.InferenceStatus.Completed -> {
                        Log.d("KardiaAI", "Completo. Respuesta de Gemma:\n$fullResponse")
                        _uiState.value = GeneratorState.Generating(
                            progress = 0.85f,
                            subtaskDescription = "Finalizando inferencia y analizando resultados..."
                        )
                        delay(400) // Transición visual suave
                        val drafts = parseFlashcards(fullResponse)
                        Log.d("KardiaAI", "Tarjetas parseadas: ${drafts.size}")
                        if (drafts.isEmpty()) {
                            // En caso de que el modelo haya alucinado y no coincida con el formato Q/A
                            _uiState.value = GeneratorState.Error(
                                "La IA local no generó tarjetas compatibles con el formato esperado. Por favor, intenta de nuevo."
                            )
                        } else {
                            _uiState.value = GeneratorState.Drafting(
                                deckName = deckName,
                                drafts = drafts
                            )
                        }
                    }
                    is LocalLLMManager.InferenceStatus.Error -> {
                        Log.e("KardiaAI", "Error de inferencia", status.exception)
                        _uiState.value = GeneratorState.Error("Error en inferencia local: ${status.exception.message}")
                    }
                }
            }
        }
    }

    /**
     * Actualiza el borrador en la lista.
     */
    fun updateDraftCard(index: Int, updatedCard: DraftCard) {
        val currentState = _uiState.value
        if (currentState is GeneratorState.Drafting) {
            val updatedList = currentState.drafts.toMutableList().apply {
                this[index] = updatedCard
            }
            _uiState.value = currentState.copy(drafts = updatedList)
        }
    }

    /**
     * Elimina una tarjeta del borrador.
     */
    fun deleteDraftCard(index: Int) {
        val currentState = _uiState.value
        if (currentState is GeneratorState.Drafting) {
            val updatedList = currentState.drafts.toMutableList().apply {
                removeAt(index)
            }
            _uiState.value = currentState.copy(drafts = updatedList)
        }
    }

    /**
     * Compila y exporta las tarjetas a un archivo .apkg local de forma 100% nativa.
     */
    fun exportToApkg(deckName: String, drafts: List<DraftCard>) {
        if (drafts.isEmpty()) {
            _uiState.value = GeneratorState.Error("No hay tarjetas para exportar.")
            return
        }

        val originalState = _uiState.value
        _uiState.value = GeneratorState.Generating(0.90f, "Compilando base de datos SQLite (.apkg)...")

        viewModelScope.launch {
            val exportDir = File(getApplication<Application>().filesDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val sanitizedDeckName = deckName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val targetFile = File(exportDir, "$sanitizedDeckName.apkg")

            val result = compiler.compile(drafts, deckName, targetFile)
            if (result.isSuccess) {
                _uiState.value = GeneratorState.Success(
                    message = "¡Mazo compilado exitosamente!",
                    detail = "Archivo exportado localmente en:\n${targetFile.absolutePath}"
                )
            } else {
                _uiState.value = GeneratorState.Error(
                    "Error al compilar el SQLite: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    /**
     * Verifica si AnkiDroid está disponible para inyectar directamente.
     */
    fun isAnkiDroidAvailable(): Boolean {
        return connector.isAnkiDroidAvailable()
    }

    /**
     * Inyecta las tarjetas directamente en la base de datos de AnkiDroid.
     */
    fun importToAnkiDroid(deckName: String, drafts: List<DraftCard>) {
        if (drafts.isEmpty()) {
            _uiState.value = GeneratorState.Error("No hay tarjetas para importar.")
            return
        }

        _uiState.value = GeneratorState.Generating(0.90f, "Conectando con la base de datos de AnkiDroid...")

        viewModelScope.launch {
            val result = connector.addNotesToAnkiDroid(deckName, drafts)
            if (result.isSuccess) {
                val insertedCount = result.getOrNull() ?: 0
                _uiState.value = GeneratorState.Success(
                    message = "¡Mazo inyectado en AnkiDroid!",
                    detail = "Se agregaron exitosamente $insertedCount de ${drafts.size} tarjetas directamente a la base de datos de AnkiDroid."
                )
            } else {
                _uiState.value = GeneratorState.Error(
                    "Error de conexión con AnkiDroid: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    /**
     * Retorna a la pantalla de entrada principal.
     */
    fun resetToIdle() {
        _uiState.value = GeneratorState.Idle()
    }

    /**
     * Parsea la respuesta en texto plano estructurado del modelo en una lista de DraftCards.
     */
    private fun parseFlashcards(text: String): List<DraftCard> {
        val list = mutableListOf<DraftCard>()
        
        // 1. Intentar buscar bloques delimitados por "---" o buscar dinámicamente Q: si olvidó separadores
        val blocks = if (text.contains("---")) {
            text.split("---")
        } else {
            // Dividir por cada vez que empieza una "Q:"
            text.split(Regex("(?=Q:)", RegexOption.IGNORE_CASE))
        }

        for (block in blocks) {
            val lines = block.trim().lines()
            var front = ""
            var back = ""

            for (line in lines) {
                val trimmed = line.trim()
                // Tolerar Q:, A:, Pregunta:, Respuesta:, **Q:**, **A:**
                if (trimmed.startsWith("Q:", ignoreCase = true)) {
                    front = trimmed.substring(2).trim()
                } else if (trimmed.startsWith("A:", ignoreCase = true)) {
                    back = trimmed.substring(2).trim()
                } else if (trimmed.startsWith("Pregunta:", ignoreCase = true)) {
                    front = trimmed.substring(9).trim()
                } else if (trimmed.startsWith("Respuesta:", ignoreCase = true)) {
                    back = trimmed.substring(10).trim()
                } else if (trimmed.startsWith("**Q:**", ignoreCase = true)) {
                    front = trimmed.substring(6).trim()
                } else if (trimmed.startsWith("**A:**", ignoreCase = true)) {
                    back = trimmed.substring(6).trim()
                }
            }

            // Sanitizar markdown bold sobrante
            front = front.replace("**", "").trim()
            back = back.replace("**", "").trim()

            if (front.isNotEmpty() && back.isNotEmpty()) {
                list.add(
                    DraftCard(
                        id = UUID.randomUUID().toString(),
                        front = front,
                        back = back
                    )
                )
            }
        }

        // 2. Fallback line-by-line pairing si lo anterior falla por bloques corruptos
        if (list.isEmpty()) {
            val lines = text.lines()
            var currentQ = ""
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("Q:", ignoreCase = true) || trimmed.startsWith("Pregunta:", ignoreCase = true)) {
                    val prefixLen = if (trimmed.startsWith("Q:", ignoreCase = true)) 2 else 9
                    currentQ = trimmed.substring(prefixLen).replace("**", "").trim()
                } else if ((trimmed.startsWith("A:", ignoreCase = true) || trimmed.startsWith("Respuesta:", ignoreCase = true)) && currentQ.isNotEmpty()) {
                    val prefixLen = if (trimmed.startsWith("A:", ignoreCase = true)) 2 else 10
                    val currentA = trimmed.substring(prefixLen).replace("**", "").trim()
                    list.add(
                        DraftCard(
                            id = UUID.randomUUID().toString(),
                            front = currentQ,
                            back = currentA
                        )
                    )
                    currentQ = ""
                }
            }
        }
        return list
    }

    override fun onCleared() {
        super.onCleared()
        llmManager.close()
    }
}
