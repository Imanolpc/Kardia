package imanolpc.kardia.core.ai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

class ModelCatalogRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("kardia_models_prefs", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // URL del catálogo en GitHub (se puede actualizar el JSON en el repo sin tocar la APK)
    private val remoteCatalogUrl = "https://raw.githubusercontent.com/Imanolpc/Kardia/main/models.json"

    private val KEY_SELECTED_MODEL_ID = "selected_model_id"
    private val KEY_CACHED_CATALOG = "cached_catalog_json"

    /**
     * Obtiene el ID del modelo actualmente seleccionado.
     */
    fun getSelectedModelId(): String {
        return prefs.getString(KEY_SELECTED_MODEL_ID, AIModelInfo.DEFAULT_CATALOG.first().id)
            ?: AIModelInfo.DEFAULT_CATALOG.first().id
    }

    /**
     * Guarda el ID del modelo seleccionado por el usuario.
     */
    fun setSelectedModelId(modelId: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL_ID, modelId).apply()
    }

    /**
     * Carga la lista completa de modelos combinando catálogo remoto (o caché),
     * modelos por defecto y modelos importados localmente.
     */
    suspend fun getAvailableModels(llmDirectory: File): List<AIModelInfo> = withContext(Dispatchers.IO) {
        val modelsMap = mutableMapOf<String, AIModelInfo>()

        // 1. Cargar modelos por defecto
        AIModelInfo.DEFAULT_CATALOG.forEach { modelsMap[it.id] = it }

        // 2. Intentar cargar desde el JSON remoto en GitHub
        try {
            val remoteJson = fetchRemoteCatalogJson()
            if (remoteJson != null) {
                prefs.edit().putString(KEY_CACHED_CATALOG, remoteJson).apply()
                parseModelsJson(remoteJson).forEach { modelsMap[it.id] = it }
            } else {
                // Si falla la red, usar caché si existe
                prefs.getString(KEY_CACHED_CATALOG, null)?.let { cachedJson ->
                    parseModelsJson(cachedJson).forEach { modelsMap[it.id] = it }
                }
            }
        } catch (e: Exception) {
            // Usar caché si está disponible
            prefs.getString(KEY_CACHED_CATALOG, null)?.let { cachedJson ->
                parseModelsJson(cachedJson).forEach { modelsMap[it.id] = it }
            }
        }

        // 3. Detectar modelos locales adicionales presentes en la carpeta llm/
        if (llmDirectory.exists() && llmDirectory.isDirectory) {
            val localFiles = llmDirectory.listFiles { file ->
                file.isFile && (file.name.endsWith(".bin") || file.name.endsWith(".task"))
            } ?: emptyArray()

            for (file in localFiles) {
                val existing = modelsMap.values.find { it.filename == file.name }
                if (existing == null) {
                    val customId = "local-" + file.nameWithoutExtension
                    modelsMap[customId] = AIModelInfo(
                        id = customId,
                        name = "Modelo Local: ${file.nameWithoutExtension}",
                        description = "Modelo importado manualmente desde el almacenamiento local.",
                        filename = file.name,
                        downloadUrl = "",
                        sizeBytes = file.length(),
                        isLocalImport = true
                    )
                }
            }
        }

        return@withContext modelsMap.values.toList()
    }

    private fun fetchRemoteCatalogJson(): String? {
        return try {
            val request = Request.Builder().url(remoteCatalogUrl).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseModelsJson(jsonString: String): List<AIModelInfo> {
        val result = mutableListOf<AIModelInfo>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    AIModelInfo(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        filename = obj.getString("filename"),
                        downloadUrl = obj.getString("downloadUrl"),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        recommendedRamMb = obj.optInt("recommendedRamMb", 4096)
                    )
                )
            }
        } catch (e: Exception) {
            // Ignorar errores de formato JSON
        }
        return result
    }
}
