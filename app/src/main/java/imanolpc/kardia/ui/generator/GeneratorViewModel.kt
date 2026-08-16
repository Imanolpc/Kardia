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

        _uiState.value = GeneratorState.Generating(0.10f, "Analizando y dividiendo apuntes en secciones...")
        generateFlashcardsChunked(notes, deckName)
    }

    /**
     * Divide los apuntes en fragmentos/párrafos óptimos para no saturar la ventana de atención
     * del modelo local Gemma-2B (INT4) y garantizar máxima fidelidad factual.
     */
    private fun splitIntoChunks(text: String): List<String> {
        val rawParagraphs = text.split(Regex("(\r?\n){2,}"))
            .map { it.trim() }
            .filter { it.length > 20 }

        if (rawParagraphs.size >= 2) {
            return rawParagraphs.take(4) // Hasta 4 párrafos clave
        }

        val lines = text.lines()
            .map { it.trim() }
            .filter { it.length > 20 }

        if (lines.size >= 2) {
            return lines.chunked(2).map { it.joinToString(" ") }.take(4)
        }

        return listOf(text.trim())
    }

    private fun generateFlashcardsChunked(notes: String, deckName: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val chunks = splitIntoChunks(notes)
            val totalChunks = chunks.size
            val allDrafts = mutableListOf<DraftCard>()

            for ((chunkIndex, chunk) in chunks.withIndex()) {
                val progressVal = 0.15f + (chunkIndex.toFloat() / totalChunks) * 0.70f
                _uiState.value = GeneratorState.Generating(
                    progress = progressVal,
                    subtaskDescription = "Procesando sección ${chunkIndex + 1} de $totalChunks..."
                )

                val prompt = """
                    <start_of_turn>user
                    Lee este texto y extrae 1 pregunta directa de estudio con su respuesta corta (1 a 4 palabras).
                    
                    REGLAS:
                    - La respuesta "A:" DEBE ser solo 1 a 4 palabras (el dato o concepto clave).
                    - PROHIBIDO escribir frases completas en "A:".
                    - Basado únicamente en el texto.

                    FORMATO DE SALIDA:
                    Q: [Pregunta directa y concisa]
                    A: [1 a 4 palabras clave]

                    TEXTO:
                    $chunk<end_of_turn>
                    <start_of_turn>model
                    Q:
                """.trimIndent()

                var chunkResponse = "Q:"

                llmManager.generateFlashcardsStream(prompt).collect { status ->
                    when (status) {
                        is LocalLLMManager.InferenceStatus.Token -> {
                            chunkResponse += status.text
                        }
                        is LocalLLMManager.InferenceStatus.Completed -> {
                            Log.d("KardiaAI", "Sección ${chunkIndex + 1}/$totalChunks completada:\n$chunkResponse")
                        }
                        is LocalLLMManager.InferenceStatus.Error -> {
                            Log.e("KardiaAI", "Error en sección ${chunkIndex + 1}", status.exception)
                        }
                        else -> {}
                    }
                }

                val cards = parseFlashcardsForChunk(chunkResponse, chunk)
                allDrafts.addAll(cards)
            }

            if (allDrafts.isEmpty()) {
                _uiState.value = GeneratorState.Error(
                    "No se pudieron generar tarjetas para este texto. Intenta con un texto más claro o breve."
                )
            } else {
                _uiState.value = GeneratorState.Drafting(
                    deckName = deckName,
                    drafts = allDrafts
                )
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
    private fun cleanAnswer(answer: String): String {
        var clean = answer.trim()
        val prefixes = listOf(
            Regex("^El\\s+[A-Za-z0-9_áéíóúÁÉÍÓÚñÑ]+\\s+es\\s+", RegexOption.IGNORE_CASE),
            Regex("^La\\s+[A-Za-z0-9_áéíóúÁÉÍÓÚñÑ]+\\s+es\\s+", RegexOption.IGNORE_CASE),
            Regex("^Los\\s+[A-Za-z0-9_áéíóúÁÉÍÓÚñÑ]+\\s+son\\s+", RegexOption.IGNORE_CASE),
            Regex("^Las\\s+[A-Za-z0-9_áéíóúÁÉÍÓÚñÑ]+\\s+son\\s+", RegexOption.IGNORE_CASE),
            Regex("^Se\\s+puede\\s+[A-Za-z0-9_áéíóúÁÉÍÓÚñÑ]+\\s+", RegexOption.IGNORE_CASE),
            Regex("^Es\\s+un\\s+", RegexOption.IGNORE_CASE),
            Regex("^Es\\s+una\\s+", RegexOption.IGNORE_CASE)
        )
        for (prefix in prefixes) {
            clean = clean.replace(prefix, "")
        }
        
        val words = clean.split(Regex("\\s+"))
        if (words.size > 5) {
            val firstComma = clean.indexOf(',')
            if (firstComma in 3..35) {
                clean = clean.substring(0, firstComma).trim()
            } else {
                clean = words.take(4).joinToString(" ")
            }
        }
        
        return clean.trim().removeSuffix(".")
    }

    private fun parseFlashcardsForChunk(text: String, sourceParagraph: String): List<DraftCard> {
        val list = mutableListOf<DraftCard>()
        val blocks = if (text.contains("---")) {
            text.split("---")
        } else {
            text.split(Regex("(?=Q:)", RegexOption.IGNORE_CASE))
        }

        for (block in blocks) {
            val lines = block.trim().lines()
            var front = ""
            var back = ""

            for (line in lines) {
                val trimmed = line.trim()
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

            front = front.replace("**", "").trim()
            back = cleanAnswer(back.replace("**", "").trim())

            // Descartar placeholders
            if (front.contains("[Pregunta") || front.contains("[Frase") || back.contains("[Respuesta") || back.contains("[Dato")) {
                continue
            }

            if (front.isNotEmpty() && back.isNotEmpty()) {
                list.add(
                    DraftCard(
                        id = UUID.randomUUID().toString(),
                        front = front,
                        back = back,
                        sourceText = sourceParagraph
                    )
                )
            }
        }

        // Fallback line-by-line pairing si los bloques fallaron
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
                    val currentA = cleanAnswer(trimmed.substring(prefixLen).replace("**", "").trim())
                    if (!currentQ.contains("[Pregunta") && !currentA.contains("[Respuesta") && currentA.isNotEmpty()) {
                        list.add(
                            DraftCard(
                                id = UUID.randomUUID().toString(),
                                front = currentQ,
                                back = currentA,
                                sourceText = sourceParagraph
                            )
                        )
                    }
                    currentQ = ""
                }
            }
        }

        // Generar SIEMPRE una tarjeta de autocompletar (Cloze) extraída literalmente del texto
        val sentences = sourceParagraph.split(Regex("(?<=[.!?])\\s+")).filter { it.trim().length > 15 }
        var clozeFront = ""
        var clozeBack = ""

        if (list.isNotEmpty()) {
            val answerKey = list.first().back
            for (sentence in sentences) {
                if (sentence.contains(answerKey, ignoreCase = true)) {
                    clozeFront = sentence.replace(
                        Regex(Regex.escape(answerKey), RegexOption.IGNORE_CASE),
                        "_______"
                    )
                    clozeBack = answerKey
                    break
                }
            }
        }

        if (clozeFront.isEmpty() && sentences.isNotEmpty()) {
            val targetSentence = sentences.first().trim()
            val words = targetSentence.split(" ").filter { it.length > 4 && !it.startsWith("http") }
            if (words.isNotEmpty()) {
                val chosenWord = words.last().trim().removeSuffix(".").removeSuffix(",")
                clozeFront = targetSentence.replace(chosenWord, "_______")
                clozeBack = chosenWord
            }
        }

        if (clozeFront.isNotEmpty() && clozeBack.isNotEmpty()) {
            list.add(
                DraftCard(
                    id = UUID.randomUUID().toString(),
                    front = clozeFront,
                    back = clozeBack,
                    sourceText = sourceParagraph
                )
            )
        }

        return list
    }

    override fun onCleared() {
        super.onCleared()
        llmManager.close()
    }
}
