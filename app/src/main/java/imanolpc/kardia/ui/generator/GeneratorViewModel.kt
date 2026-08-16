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
                Eres un motor de extracción factual estricto en español para tarjetas de estudio Anki.
                Tu tarea es leer el TEXTO y generar EXACTAMENTE 3 tarjetas de estudio basándote ÚNICAMENTE en la información literal provista.

                REGLAS CRÍTICAS DE FIDELIDAD Y FORMATO:
                1. 100% FIEL AL TEXTO: Está terminantemente prohibido inventar, asumir o usar conocimientos externos. Si un dato no está mencionado explícitamente en el TEXTO, no existirá ninguna tarjeta sobre él.
                2. RESPUESTAS CORTAS: El campo "A:" (respuesta) debe contener entre 1 y 4 palabras como máximo. Las preguntas "Q:" pueden ser largas y detalladas para dar contexto.
                3. ESTRUCTURA DE 3 TARJETAS:
                   - Tarjeta 1: Pregunta directa sobre el texto. Formato:
                     Q: [Pregunta]
                     A: [Respuesta de 1-4 palabras]
                     S: [Cita literal del párrafo exacto de origen]
                   - Tarjeta 2: Pregunta directa sobre el texto. Formato:
                     Q: [Pregunta]
                     A: [Respuesta de 1-4 palabras]
                     S: [Cita literal del párrafo exacto de origen]
                   - Tarjeta 3: Rellenar el hueco (frase incompleta del texto con "_______"). Formato:
                     Q: [Frase con _______ en lugar del concepto clave]
                     A: [Concepto clave de 1-4 palabras]
                     S: [Cita literal del párrafo exacto de origen]
                4. FORMATO DE SALIDA SECO: Responde únicamente con los bloques separados por "---". No incluyas explicaciones, viñetas ni enumeraciones de las tarjetas.

                Ejemplo de formato:
                Texto: "La fotosíntesis es el proceso de conversión de luz solar en energía química usando dióxido de carbono y agua."
                Q: ¿Qué tipo de conversión realiza la fotosíntesis con la luz solar?
                A: Energía química.
                S: La fotosíntesis es el proceso de conversión de luz solar en energía química usando dióxido de carbono y agua.
                ---
                Q: ¿Qué compuestos se utilizan junto con la luz solar en la fotosíntesis?
                A: Dióxido de carbono y agua.
                S: La fotosíntesis es el proceso de conversión de luz solar en energía química usando dióxido de carbono y agua.
                ---
                Q: La fotosíntesis convierte la luz solar en energía química usando dióxido de carbono y _______
                A: Agua.
                S: La fotosíntesis es el proceso de conversión de luz solar en energía química usando dióxido de carbono y agua.
                ---

                TEXTO A PROCESAR:
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
