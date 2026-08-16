package imanolpc.kardia.core.util

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.BufferedReader
import java.io.InputStreamReader

object DocumentParser {

    /**
     * Extrae texto de una URI seleccionada (admite PDF y TXT).
     */
    fun extractTextFromUri(context: Context, uri: Uri): String {
        val contentResolver = context.contentResolver
        val type = contentResolver.getType(uri) ?: ""
        
        // Obtener el nombre del archivo si es posible
        var fileName = ""
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex).lowercase()
            }
        }

        return when {
            type.contains("pdf") || fileName.endsWith(".pdf") -> {
                extractTextFromPdf(context, uri)
            }
            type.contains("text") || type.contains("plain") || fileName.endsWith(".txt") -> {
                extractTextFromTxt(context, uri)
            }
            else -> {
                // Intentar leer como texto por defecto si es otro formato
                extractTextFromTxt(context, uri)
            }
        }
    }

    private fun extractTextFromPdf(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri).use { inputStream ->
                if (inputStream == null) return "Error: No se pudo abrir el archivo PDF."
                val document = PDDocument.load(inputStream)
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)
                document.close()
                text
            }
        } catch (e: Exception) {
            "Error al extraer texto del PDF: ${e.message}"
        }
    }

    private fun extractTextFromTxt(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri).use { inputStream ->
                if (inputStream == null) return "Error: No se pudo abrir el archivo TXT."
                val reader = BufferedReader(InputStreamReader(inputStream))
                val builder = StringBuilder()
                var line = reader.readLine()
                while (line != null) {
                    builder.append(line).append("\n")
                    line = reader.readLine()
                }
                builder.toString()
            }
        } catch (e: Exception) {
            "Error al extraer texto del TXT: ${e.message}"
        }
    }
}
