package com.kardia.app.core.anki

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DraftCard(
    val id: String,
    val front: String,
    val back: String,
    val tags: Set<String> = emptySet()
)

class AnkiApkgCompiler(private val context: Context) {

    companion object {
        private const val STATIC_MODEL_ID = 1600000000001L
        private const val STATIC_DECK_ID = 1600000000002L
        private const val ANKI_FIELD_SEPARATOR = "\u001f"
    }

    /**
     * Compila una lista de tarjetas de borrador en un archivo .apkg en la ruta dada.
     * Se ejecuta de forma asíncrona en Dispatchers.IO.
     */
    suspend fun compile(
        cards: List<DraftCard>,
        deckName: String,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "anki_compile_${System.currentTimeMillis()}")
        if (!tempDir.exists()) tempDir.mkdirs()

        val dbFile = File(tempDir, "collection.anki2")
        var db: SQLiteDatabase? = null

        return@withContext try {
            // 1. Crear la base de datos SQLite local
            db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)

            // 2. Crear las tablas necesarias del esquema Anki2
            createSchema(db)

            // 3. Crear los JSON de metadatos para la tabla 'col'
            val currentTimeSeconds = System.currentTimeMillis() / 1000
            val currentTimeMillis = System.currentTimeMillis()

            val confJson = buildConfJson(STATIC_MODEL_ID)
            val modelsJson = buildModelsJson(STATIC_MODEL_ID, currentTimeSeconds)
            val decksJson = buildDecksJson(STATIC_DECK_ID, deckName, currentTimeSeconds)
            val dconfJson = buildDconfJson(currentTimeSeconds)

            // Insertar metadatos únicos de colección
            db.execSQL(
                """
                INSERT INTO col (id, crt, mod, scm, ver, dty, usn, ls, conf, models, decks, dconf, tags)
                VALUES (1, ?, ?, ?, 11, 0, 0, 0, ?, ?, ?, ?, '{}')
                """.trimIndent(),
                arrayOf(
                    currentTimeSeconds, // crt
                    currentTimeMillis,  // mod
                    currentTimeMillis,  // scm
                    confJson,           // conf
                    modelsJson,         // models
                    decksJson,          // decks
                    dconfJson           // dconf
                )
            )

            // 4. Insertar notas y tarjetas correspondientes
            cards.forEachIndexed { index, card ->
                val noteId = currentTimeMillis + (index * 2)
                val cardId = noteId + 1
                val guid = generateStableGuid(card.front)
                val modSeconds = currentTimeSeconds
                val modMillis = currentTimeMillis
                val flds = "${card.front}$ANKI_FIELD_SEPARATOR${card.back}"
                val sfld = card.front
                val csum = calculateAnkiChecksum(card.front)
                val tagsStr = card.tags.joinToString(" ")

                // Insertar nota
                db.execSQL(
                    """
                    INSERT INTO notes (id, guid, mid, mod, usn, tags, flds, sfld, csum, flags, data)
                    VALUES (?, ?, ?, ?, -1, ?, ?, ?, ?, 0, '')
                    """.trimIndent(),
                    arrayOf(
                        noteId,
                        guid,
                        STATIC_MODEL_ID,
                        modSeconds,
                        tagsStr,
                        flds,
                        sfld,
                        csum
                    )
                )

                // Insertar tarjeta (ord = 0 es la primera plantilla, frontal -> reverso)
                db.execSQL(
                    """
                    INSERT INTO cards (id, nid, did, ord, mod, usn, type, queue, due, ivl, factor, reps, lapses, left, odue, odid, flags, data)
                    VALUES (?, ?, ?, 0, ?, -1, 0, 0, 0, 0, 2500, 0, 0, 0, 0, 0, 0, '')
                    """.trimIndent(),
                    arrayOf(
                        cardId,
                        noteId,
                        STATIC_DECK_ID,
                        modMillis
                    )
                )
            }

            // Cerrar base de datos antes de comprimir
            db.close()
            db = null

            // 5. Crear el archivo JSON de mapeo multimedia (vacío en nuestro MVP por ser texto)
            val mediaFile = File(tempDir, "media")
            FileOutputStream(mediaFile).use { fos ->
                fos.write("{}".toByteArray(Charsets.UTF_8))
            }

            // 6. Comprimir collection.anki2 y media en el archivo .apkg (formato ZIP)
            zipFiles(listOf(dbFile, mediaFile), outputFile)

            Result.success(outputFile)
        } catch (e: Exception) {
            db?.close()
            Result.failure(e)
        } finally {
            // Eliminar directorio temporal
            tempDir.deleteRecursively()
        }
    }

    /**
     * Crea las tablas y los índices mínimos necesarios en la base de datos de Anki.
     */
    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE cards (
                id integer primary key,
                nid integer not null,
                did integer not null,
                ord integer not null,
                mod integer not null,
                usn integer not null,
                type integer not null,
                queue integer not null,
                due integer not null,
                ivl integer not null,
                factor integer not null,
                reps integer not null,
                lapses integer not null,
                left integer not null,
                odue integer not null,
                odid integer not null,
                flags integer not null,
                data text not null
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE notes (
                id integer primary key,
                guid text not null,
                mid integer not null,
                mod integer not null,
                usn integer not null,
                tags text not null,
                flds text not null,
                sfld text not null,
                csum integer not null,
                flags integer not null,
                data text not null
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE col (
                id integer primary key,
                crt integer not null,
                mod integer not null,
                scm integer not null,
                ver integer not null,
                dty integer not null,
                usn integer not null,
                ls integer not null,
                conf text not null,
                models text not null,
                decks text not null,
                dconf text not null,
                tags text not null
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE graves (
                usn integer not null,
                oid integer not null,
                type integer not null
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE revlog (
                id integer primary key,
                cid integer not null,
                usn integer not null,
                ease integer not null,
                ivl integer not null,
                lastIvl integer not null,
                factor integer not null,
                time integer not null,
                type integer not null
            )
            """.trimIndent()
        )

        // Índices estándar de Anki para acelerar las búsquedas
        db.execSQL("CREATE INDEX f_cards_nid ON cards (nid)")
        db.execSQL("CREATE INDEX f_cards_did ON cards (did)")
        db.execSQL("CREATE INDEX f_notes_mid ON notes (mid)")
        db.execSQL("CREATE INDEX f_notes_sfld ON notes (sfld)")
    }

    /**
     * Genera un hash SHA-256 consistente de 10 caracteres codificados en Base64 para notes.guid.
     */
    fun generateStableGuid(sourceText: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(sourceText.toByteArray(Charsets.UTF_8))
        // Reemplazar caracteres no válidos para el guid de Anki si es necesario, o usar Base64 simplificado
        val base64 = Base64.encodeToString(hash, Base64.NO_PADDING or Base64.NO_WRAP)
        // Convertimos a un String alfanumérico seguro para el guid (10 caracteres)
        return base64.replace('+', '-').replace('/', '_').take(10)
    }

    /**
     * Calcula la suma de verificación de Anki (los primeros 4 bytes del hash SHA-1 convertidos a Long de 32 bits).
     */
    fun calculateAnkiChecksum(text: String): Long {
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(text.toByteArray(Charsets.UTF_8))
        var value = 0L
        for (i in 0..3) {
            value = (value shl 8) or (hash[i].toLong() and 0xFF)
        }
        return value
    }

    // --- CONSTRUCCIÓN DE ESTRUCTURAS JSON ESTÁTICAS DE ANKI ---

    private fun buildConfJson(modelId: Long): String {
        return """
        {
            "nextPos": 1,
            "estTimes": true,
            "activeDecks": [$STATIC_DECK_ID],
            "sortType": "noteFld",
            "timeLim": 0,
            "sortBackwards": false,
            "collapseTime": 1200,
            "curDeck": $STATIC_DECK_ID,
            "newSpread": 0,
            "addToCur": true,
            "newBury": true,
            "curModel": $modelId
        }
        """.trimIndent().replace(Regex("\\s+"), "")
    }

    private fun buildModelsJson(modelId: Long, creationTime: Long): String {
        // qfmt: Pregunta (Frontal), afmt: Respuesta (Frontal + Separador + Reverso)
        val qfmt = "{{Front}}"
        val afmt = "{{Front}}\\n\\n<hr id=answer>\\n\\n{{Back}}"
        val css = ".card { font-family: arial; font-size: 20px; text-align: center; color: black; background-color: white; }"

        return """
        {
            "$modelId": {
                "id": $modelId,
                "name": "Kardia Local AI Model",
                "type": 0,
                "mod": $creationTime,
                "usn": -1,
                "sortf": 0,
                "did": $STATIC_DECK_ID,
                "flds": [
                    {
                        "name": "Front",
                        "media": [],
                        "rtl": false,
                        "sticky": false,
                        "ord": 0,
                        "font": "Arial",
                        "size": 20
                    },
                    {
                        "name": "Back",
                        "media": [],
                        "rtl": false,
                        "sticky": false,
                        "ord": 1,
                        "font": "Arial",
                        "size": 20
                    }
                ],
                "tmpls": [
                    {
                        "name": "Card 1",
                        "qfmt": "$qfmt",
                        "afmt": "$afmt",
                        "did": null,
                        "ord": 0,
                        "bafmt": "",
                        "bqfmt": ""
                    }
                ],
                "css": "$css",
                "vers": []
            }
        }
        """.trimIndent().replace(Regex("\\n\\s*"), "")
    }

    private fun buildDecksJson(deckId: Long, deckName: String, creationTime: Long): String {
        return """
        {
            "1": {
                "id": 1,
                "mod": 0,
                "name": "Default",
                "usn": -1,
                "lrnToday": [0, 0],
                "revToday": [0, 0],
                "newToday": [0, 0],
                "timeToday": [0, 0],
                "collapsed": false,
                "desc": "",
                "dyn": 0,
                "conf": 1,
                "extendNew": 10,
                "extendRev": 50
            },
            "$deckId": {
                "id": $deckId,
                "mod": $creationTime,
                "name": "$deckName",
                "usn": -1,
                "lrnToday": [0, 0],
                "revToday": [0, 0],
                "newToday": [0, 0],
                "timeToday": [0, 0],
                "collapsed": false,
                "desc": "Mazo autogenerado localmente por Kardia MVP.",
                "dyn": 0,
                "conf": 1,
                "extendNew": 10,
                "extendRev": 50
            }
        }
        """.trimIndent().replace(Regex("\\n\\s*"), "")
    }

    private fun buildDconfJson(creationTime: Long): String {
        return """
        {
            "1": {
                "id": 1,
                "mod": $creationTime,
                "name": "Default",
                "usn": -1,
                "maxTaken": 60,
                "new": {
                    "delays": [1.0, 10.0],
                    "ints": [1, 4, 7],
                    "initialFactor": 2500,
                    "order": 1,
                    "bury": false,
                    "perDay": 20
                },
                "rev": {
                    "bury": false,
                    "ivlFct": 1.0,
                    "maxIvl": 36500,
                    "ease4": 1.3,
                    "fuzz": 0.05,
                    "perDay": 200,
                    "minSpace": 1
                },
                "lapse": {
                    "delays": [10.0],
                    "mult": 0.0,
                    "minInt": 1,
                    "leechFlds": 8,
                    "leechAction": 0
                },
                "dyn": false
            }
        }
        """.trimIndent().replace(Regex("\\n\\s*"), "")
    }

    /**
     * Comprime una lista de archivos en un archivo ZIP/APKG de destino.
     */
    private fun zipFiles(files: List<File>, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
            val buffer = ByteArray(1024 * 4)
            for (file in files) {
                FileInputStream(file).use { fi ->
                    val entry = ZipEntry(file.name)
                    out.putNextEntry(entry)
                    var count: Int
                    while (fi.read(buffer).also { count = it } != -1) {
                        out.write(buffer, 0, count)
                    }
                }
            }
        }
    }
}
