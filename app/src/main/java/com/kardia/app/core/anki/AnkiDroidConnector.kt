package com.kardia.app.core.anki

import android.content.Context
import com.ichi2.anki.api.AddContentApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class AnkiDroidConnector(private val context: Context) {

    /**
     * Instancia un cliente de la API de AnkiDroid de forma segura.
     * Mitiga errores de arranque en frío capturando excepciones del proveedor de contenidos.
     */
    private fun getApiSafe(): AddContentApi? {
        return try {
            val packageName = AddContentApi.getAnkiDroidPackageName(context)
            if (packageName != null) {
                // Captura defensiva para IllegalArgumentException ("Must set a non-null context...")
                // que ocurre si se invoca tras encender el dispositivo y AnkiDroid está inactivo.
                AddContentApi(context)
            } else {
                null
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            // Retorna null para forzar un reintento limpio en flujos superiores
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Verifica si AnkiDroid está instalado en el dispositivo.
     */
    fun isAnkiDroidAvailable(): Boolean {
        return try {
            AddContentApi.getAnkiDroidPackageName(context) != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Inyecta una lista de tarjetas de borrador directamente en la base de datos de AnkiDroid.
     * Implementa un patrón defensivo estricto con reintentos y retroceso exponencial (Backoff)
     * para evitar colisiones cuando el usuario está repasando tarjetas.
     */
    suspend fun addNotesToAnkiDroid(
        deckName: String,
        notes: List<DraftCard>
    ): Result<Int> = withContext(Dispatchers.IO) {
        var api = getApiSafe()
        if (api == null) {
            // Intento de recuperación inmediata en caso de arranque en frío
            delay(300)
            api = getApiSafe()
            if (api == null) {
                return@withContext Result.failure(
                    Exception("AnkiDroid no responde o no está instalado.")
                )
            }
        }

        // Obtener o crear mazo
        val deckId = findOrCreateDeck(api, deckName) ?: return@withContext Result.failure(
            Exception("No se pudo acceder o crear el mazo '$deckName' en AnkiDroid.")
        )

        // Obtener o crear tipo de nota (modelo)
        val modelId = findOrCreateModel(api) ?: return@withContext Result.failure(
            Exception("No se pudo registrar el tipo de nota en AnkiDroid.")
        )

        var successCount = 0

        for (note in notes) {
            var inserted = false
            var attempts = 0
            val maxAttempts = 3
            var delayMs = 500L

            val fields = arrayOf(note.front, note.back)
            val tags = note.tags

            while (!inserted && attempts < maxAttempts) {
                try {
                    // Inserción relacional interprocesos (IPC ContentProvider)
                    val addedNoteId = api.addNote(modelId, deckId, fields, tags)
                    if (addedNoteId != null) {
                        successCount++
                        inserted = true
                    } else {
                        attempts++
                    }
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    // Captura de BackendInvalidInputException ("card was modified") o SQLiteDatabaseLockedException
                    // por colisiones concurrentes (el usuario repasa en AnkiDroid mientras escribimos).
                    if (msg.contains("card was modified") || msg.contains("locked") || e is IllegalArgumentException) {
                        attempts++
                        if (attempts < maxAttempts) {
                            delay(delayMs)
                            delayMs *= 2 // Backoff exponencial
                        }
                    } else {
                        // Error crítico no relacionado con concurrencia
                        return@withContext Result.failure(e)
                    }
                }
            }
        }

        return@withContext Result.success(successCount)
    }

    /**
     * Busca el mazo por nombre; si no existe, lo crea.
     */
    private fun findOrCreateDeck(api: AddContentApi, deckName: String): Long? {
        return try {
            val deckList = api.deckList
            val existingDeckId = deckList?.entries?.find {
                it.value.equals(deckName, ignoreCase = true)
            }?.key
            
            existingDeckId ?: api.addNewDeck(deckName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Busca o crea el Note Type estático de Kardia en AnkiDroid.
     */
    private fun findOrCreateModel(api: AddContentApi): Long? {
        val modelName = "Kardia Local AI Model"
        return try {
            val modelList = api.modelList
            val existingModelId = modelList?.entries?.find {
                it.value.equals(modelName, ignoreCase = true)
            }?.key

            existingModelId ?: api.addNewModel(
                modelName,
                arrayOf("Front", "Back"), // Campos
                arrayOf("Card 1"),        // Nombre de plantilla
                arrayOf("{{Front}}"),     // QFmt
                arrayOf("{{Front}}<br><hr id=answer>{{Back}}"), // AFmt
                null, // CSS por defecto
                null, // Mazo por defecto (ninguno)
                null  // Tipo por defecto (0 = standard)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
