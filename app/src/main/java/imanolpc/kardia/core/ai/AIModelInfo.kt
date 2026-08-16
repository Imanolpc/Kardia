package imanolpc.kardia.core.ai

/**
 * Representa la información de un modelo LLM disponible para inferencia local.
 */
data class AIModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val filename: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val recommendedRamMb: Int = 4096,
    val isLocalImport: Boolean = false
) {
    /**
     * Formatea el tamaño en MB o GB de forma legible para el usuario.
     */
    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "Desconocido"
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1024.0) {
                String.format("%.2f GB", mb / 1024.0)
            } else {
                String.format("%.0f MB", mb)
            }
        }

    companion object {
        /**
         * Catálogo base precargado en caso de no haber conexión a internet
         * o para arranque instantáneo sin esperar al endpoint remoto.
         */
        val DEFAULT_CATALOG = listOf(
            AIModelInfo(
                id = "gemma-2b-it-int4",
                name = "Gemma 2B IT (Recomendado)",
                description = "Modelo equilibrado de Google optimizado en 4-bits. Excelente precisión y fidelidad factual.",
                filename = "gemma-2b-it-cpu-int4.bin",
                downloadUrl = "https://huggingface.co/google/gemma-2b-it-cpu-int4/resolve/main/gemma-2b-it-cpu-int4.bin",
                sizeBytes = 1_350_000_000L,
                recommendedRamMb = 4096
            ),
            AIModelInfo(
                id = "gemma-3-1b-it-int4",
                name = "Gemma 3 1B IT (Ligero y Rápido)",
                description = "Última generación ultraligera de Google. Ideal para dispositivos con 4GB o 6GB de RAM.",
                filename = "gemma-3-1b-it-cpu-int4.bin",
                downloadUrl = "https://huggingface.co/google/gemma-3-1b-it-cpu-int4/resolve/main/gemma-3-1b-it-cpu-int4.bin",
                sizeBytes = 750_000_000L,
                recommendedRamMb = 3072
            ),
            AIModelInfo(
                id = "smollm-135m-it",
                name = "SmolLM 135M (Ultra Rápido)",
                description = "Modelo miniaturizado de alta velocidad para generar tarjetas simples de forma instantánea.",
                filename = "smollm-135m-it.bin",
                downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM-135M-Instruct/resolve/main/smollm-135m-it.bin",
                sizeBytes = 280_000_000L,
                recommendedRamMb = 2048
            )
        )
    }
}
