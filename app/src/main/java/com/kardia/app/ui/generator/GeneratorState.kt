package com.kardia.app.ui.generator

import com.kardia.app.core.anki.DraftCard

sealed interface GeneratorState {
    
    /**
     * El modelo no existe en local. Requiere descarga OTA.
     */
    data class ModelNotDownloaded(
        val downloadProgress: Float = 0f,
        val downloadMessage: String = "",
        val isDownloading: Boolean = false,
        val errorMessage: String? = null
    ) : GeneratorState

    /**
     * El modelo está listo para recibir apuntes y generar tarjetas.
     */
    data class Idle(
        val notesInput: String = "",
        val deckName: String = "Kardia AI Deck",
        val errorMessage: String? = null
    ) : GeneratorState

    /**
     * Generación de tarjetas activada. La CPU y RAM están saturadas por LiteRT-LM.
     * Muestra el banner LMK de alta prioridad y progreso detallado.
     */
    data class Generating(
        val progress: Float = 0f,
        val subtaskDescription: String = "Iniciando motor de IA local...",
        val partialText: String = ""
    ) : GeneratorState

    /**
     * Workflow de Calidad (Drafting): Tarjetas autogeneradas listas para corrección manual.
     * Previene las alucinaciones de la IA local antes de guardarlas definitivamente.
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
