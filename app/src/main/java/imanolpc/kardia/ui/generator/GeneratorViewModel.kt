package imanolpc.kardia.ui.generator

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import imanolpc.kardia.core.ai.LocalLLMManager
import imanolpc.kardia.core.anki.AnkiApkgCompiler
import imanolpc.kardia.core.anki.AnkiDroidConnector
import imanolpc.kardia.core.anki.DraftCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.net.Uri
import imanolpc.kardia.core.util.DocumentParser
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
        generateFlashcardsInternal(notes, deckName, 0)
    }

    private fun generateFlashcardsInternal(notes: String, deckName: String, retryNum: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            // Estructuramos el prompt con el template oficial de chat de Gemma, ejemplo concreto
            // y arnés de pre-completado del primer token 'Q:' para forzar rigidez en el formato.
            val prompt = """
                <start_of_turn>user
                Eres un generador de tarjetas de estudio (flashcards) profesional para Anki. Tu objetivo es analizar el texto proporcionado y generar OBLIGATORIAMENTE entre 3 y 6 tarjetas de alta calidad en español.
                Cada tarjeta consta de un anverso (Q: pregunta o frase con hueco), un reverso (A: respuesta o concepto oculto) y el párrafo de origen (S: el párrafo exacto y literal del texto del cual se ha extraído la información).

                Sigue estrictamente estas reglas de calidad y formato:
                1. **Fidelidad absoluta al texto (Cero alucinaciones):** Está TERMINANTEMENTE PROHIBIDO inventar hechos, conceptos o relaciones. Todo debe ser extraído estrictamente del texto provisto. No uses conocimientos externos.
                2. **Longitud de Preguntas y Respuestas:** Las preguntas (Q) deben ser claras, detalladas y dar suficiente contexto (pueden ser largas). Las respuestas (A) deben ser extremadamente cortas y concisas (1 a 5 palabras, máximo una frase muy breve).
                3. **Variedad de tipos de tarjetas:** Genera una mezcla de:
                   - **Preguntas directas:** Ej: Q: ¿Cómo se llama el proceso X? A: Proceso Y.
                   - **Rellenar el texto (Texto con huecos):** Escribe una frase clave del texto y reemplaza una palabra importante por `_______`. Ej: Q: La capital de Francia es _______ A: París.
                   *ATENCIÓN:* Al menos una de las tarjetas generadas debe ser obligatoriamente del tipo "Rellenar el texto" (con hueco `_______`).
                4. **Párrafo de Origen (S):** Para cada tarjeta, debes añadir la línea "S: [párrafo exacto del texto original]".
                5. **Cero texto extra:** NUNCA uses listas numeradas (1., 2.), viñetas (-) ni introducciones. Responde únicamente con preguntas, respuestas y párrafo de origen en el formato Q/A/S.

                Ejemplo de formato:
                Texto: "La fotosíntesis es el proceso de conversión de luz solar en energía química usando dióxido de carbono y agua."
                Q: ¿Cómo se llama el proceso biológico por el cual las plantas convierten la luz del sol en energía química?
                A: La fotosíntesis.
                S: La fotosíntesis es el proceso de conversión de luz solar en energía química usando dióxido de carbono y agua.
                ---
                Q: Las plantas realizan la fotosíntesis utilizando luz solar, dióxido de carbono y _______
                A: Agua.
                S: La fotosíntesis es el proceso de conversión de luz solar en energía química usando dióxido de carbono y agua.
                ---

                Texto a procesar:
                $notes<end_of_turn>
                <start_of_turn>model
                Q:
            """.trimIndent()

            // Pre-cargamos el primer token 'Q:' en el acumulador para que coincida con el arnés
            var fullResponse = "Q:"
            var cardWritingProgress = 0.20f + (retryNum * 0.15f)

            llmManager.generateFlashcardsStream(prompt).collect { status ->
                when (status) {
                    is LocalLLMManager.InferenceStatus.Starting -> {
                        _uiState.value = GeneratorState.Generating(
                            progress = 0.20f + (retryNum * 0.15f),
                            subtaskDescription = if (retryNum == 0) "Gemma-3 1B IT analizando texto..." else "Reintentando por formato (Intento ${retryNum + 1} de 3)..."
                        )
                    }
                    is LocalLLMManager.InferenceStatus.Token -> {
                        fullResponse += status.text
                        Log.d("KardiaAI", "Token: '${status.text}'")
                        if (cardWritingProgress < 0.80f) {
                            cardWritingProgress += 0.005f
                        }
                        _uiState.value = GeneratorState.Generating(
                            progress = cardWritingProgress,
                            subtaskDescription = "IA local redactando tarjetas...",
                            partialText = fullResponse
                        )
                    }
                    is LocalLLMManager.InferenceStatus.Completed -> {
                        Log.d("KardiaAI", "Inferencia completa (Intento ${retryNum + 1}). Respuesta:\n$fullResponse")
                        _uiState.value = GeneratorState.Generating(
                            progress = 0.85f,
                            subtaskDescription = "Analizando resultados generados..."
                        )
                        delay(300)
                        val drafts = parseFlashcards(fullResponse)
                        Log.d("KardiaAI", "Tarjetas parseadas: ${drafts.size}")

                        if (drafts.isEmpty()) {
                            if (retryNum < 2) {
                                Log.w("KardiaAI", "Intento ${retryNum + 1} fallido (formato vacío). Reintentando...")
                                generateFlashcardsInternal(notes, deckName, retryNum + 1)
                            } else {
                                _uiState.value = GeneratorState.Error(
                                    "La IA local no generó tarjetas compatibles tras 3 intentos. Por favor, simplifica tus apuntes e intenta nuevamente."
                                )
                            }
                        } else {
                            _uiState.value = GeneratorState.Drafting(
                                deckName = deckName,
                                drafts = drafts
                            )
                        }
                    }
                    is LocalLLMManager.InferenceStatus.Error -> {
                        Log.e("KardiaAI", "Error de inferencia (Intento ${retryNum + 1})", status.exception)
                        if (retryNum < 2) {
                            Log.w("KardiaAI", "Reintentando tras error técnico...")
                            generateFlashcardsInternal(notes, deckName, retryNum + 1)
                        } else {
                            _uiState.value = GeneratorState.Error("Error en inferencia local: ${status.exception.message}")
                        }
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
            var sourceText = ""

            for (line in lines) {
                val trimmed = line.trim()
                // Tolerar Q:, A:, S:, Pregunta:, Respuesta:, Contexto:, Origen:, **Q:**, **A:**, **S:**
                if (trimmed.startsWith("Q:", ignoreCase = true)) {
                    front = trimmed.substring(2).trim()
                } else if (trimmed.startsWith("A:", ignoreCase = true)) {
                    back = trimmed.substring(2).trim()
                } else if (trimmed.startsWith("S:", ignoreCase = true)) {
                    sourceText = trimmed.substring(2).trim()
                } else if (trimmed.startsWith("Pregunta:", ignoreCase = true)) {
                    front = trimmed.substring(9).trim()
                } else if (trimmed.startsWith("Respuesta:", ignoreCase = true)) {
                    back = trimmed.substring(10).trim()
                } else if (trimmed.startsWith("Contexto:", ignoreCase = true)) {
                    sourceText = trimmed.substring(9).trim()
                } else if (trimmed.startsWith("Origen:", ignoreCase = true)) {
                    sourceText = trimmed.substring(7).trim()
                } else if (trimmed.startsWith("**Q:**", ignoreCase = true)) {
                    front = trimmed.substring(6).trim()
                } else if (trimmed.startsWith("**A:**", ignoreCase = true)) {
                    back = trimmed.substring(6).trim()
                } else if (trimmed.startsWith("**S:**", ignoreCase = true)) {
                    sourceText = trimmed.substring(6).trim()
                }
            }

            // Sanitizar markdown bold sobrante
            front = front.replace("**", "").trim()
            back = back.replace("**", "").trim()
            sourceText = sourceText.replace("**", "").trim()

            if (front.isNotEmpty() && back.isNotEmpty()) {
                list.add(
                    DraftCard(
                        id = UUID.randomUUID().toString(),
                        front = front,
                        back = back,
                        sourceText = sourceText
                    )
                )
            }
        }

        // 2. Fallback line-by-line pairing si lo anterior falla por bloques corruptos
        if (list.isEmpty()) {
            val lines = text.lines()
            var currentQ = ""
            var currentS = ""
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("Q:", ignoreCase = true) || trimmed.startsWith("Pregunta:", ignoreCase = true)) {
                    val prefixLen = if (trimmed.startsWith("Q:", ignoreCase = true)) 2 else 9
                    currentQ = trimmed.substring(prefixLen).replace("**", "").trim()
                } else if (trimmed.startsWith("S:", ignoreCase = true) || trimmed.startsWith("Contexto:", ignoreCase = true) || trimmed.startsWith("Origen:", ignoreCase = true)) {
                    val prefixLen = when {
                        trimmed.startsWith("S:", ignoreCase = true) -> 2
                        trimmed.startsWith("Origen:", ignoreCase = true) -> 7
                        else -> 9
                    }
                    currentS = trimmed.substring(prefixLen).replace("**", "").trim()
                } else if ((trimmed.startsWith("A:", ignoreCase = true) || trimmed.startsWith("Respuesta:", ignoreCase = true)) && currentQ.isNotEmpty()) {
                    val prefixLen = if (trimmed.startsWith("A:", ignoreCase = true)) 2 else 10
                    val currentA = trimmed.substring(prefixLen).replace("**", "").trim()
                    list.add(
                        DraftCard(
                            id = UUID.randomUUID().toString(),
                            front = currentQ,
                            back = currentA,
                            sourceText = currentS
                        )
                    )
                    currentQ = ""
                    currentS = ""
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
