package imanolpc.kardia.ui.generator

import imanolpc.kardia.core.ai.AIModelInfo
import imanolpc.kardia.core.anki.DraftCard

sealed interface GeneratorState {
    
    /**
     * El modelo no existe en local. Requiere descarga OTA o importación.
     */
    data class ModelNotDownloaded(
        val downloadProgress: Float = 0f,
        val downloadMessage: String = "",
        val isDownloading: Boolean = false,
        val errorMessage: String? = null,
        val selectedModel: AIModelInfo? = null
    ) : GeneratorState

    /**
     * El modelo está descargado y listo para recibir apuntes y generar tarjetas.
     * La RAM NO está ocupada por el LLM hasta que se pulse generar.
     */
    data class Idle(
        val notesInput: String = "",
        val deckName: String = "Kardia AI Deck",
        val activeModel: AIModelInfo? = null,
        val errorMessage: String? = null
    ) : GeneratorState

    /**
     * Generación de tarjetas activada. LiteRT-LM se carga bajo demanda.
     * Muestra progreso detallado y estimación.
     */
    data class Generating(
        val progress: Float = 0f,
        val subtaskDescription: String = "Iniciando motor de IA local...",
        val partialText: String = ""
    ) : GeneratorState

    /**
     * Workflow de Calidad (Drafting): Tarjetas autogeneradas listas para corrección manual.
     * El modelo ya ha sido liberado de la RAM.
     */
    data class Drafting(
        val deckName: String,
        val drafts: List<DraftCard>
    ) : GeneratorState

    /**
     * Compilación o exportación final exitosa.
     */
    data class Success(
        val message: String,
        val detail: String
    ) : GeneratorState

    /**
     * Error general recuperable en el proceso.
     */
    data class Error(val message: String) : GeneratorState
}
