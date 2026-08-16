package imanolpc.kardia.core.database

import android.content.Context
import imanolpc.kardia.core.anki.AnkiApkgCompiler
import imanolpc.kardia.core.anki.DraftCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

enum class ReviewRating(val quality: Int) {
    AGAIN(1), // Fallé
    HARD(2),  // Difícil
    GOOD(3),  // Bueno / Recordé
    EASY(4)   // Muy fácil
}

class DeckRepository(private val context: Context) {

    private val db = KardiaDatabase.getInstance(context)
    private val deckDao = db.deckDao()
    private val flashcardDao = db.flashcardDao()
    private val compiler = AnkiApkgCompiler(context)

    fun getAllDecksWithStats(): Flow<List<DeckWithCardCount>> {
        return deckDao.getDecksWithStats()
    }

    fun getDeckWithCardsFlow(deckId: Long): Flow<DeckWithCards?> {
        return deckDao.getDeckWithCardsFlow(deckId)
    }

    suspend fun getDeckWithCards(deckId: Long): DeckWithCards? = withContext(Dispatchers.IO) {
        deckDao.getDeckWithCards(deckId)
    }

    suspend fun getDueCards(deckId: Long): List<FlashcardEntity> = withContext(Dispatchers.IO) {
        val due = flashcardDao.getDueCardsForDeck(deckId)
        if (due.isNotEmpty()) {
            due
        } else {
            // Si no hay pendientes por fecha, retornar todas para repaso libre
            flashcardDao.getCardsForDeck(deckId)
        }
    }

    suspend fun saveDeckWithDraftCards(deckName: String, drafts: List<DraftCard>): Long = withContext(Dispatchers.IO) {
        val deck = DeckEntity(
            name = deckName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val deckId = deckDao.insertDeck(deck)

        val cards = drafts.map { draft ->
            FlashcardEntity(
                deckId = deckId,
                front = draft.front,
                back = draft.back,
                sourceContext = draft.sourceText,
                createdAt = System.currentTimeMillis()
            )
        }

        flashcardDao.insertFlashcards(cards)
        deckId
    }

    suspend fun addCardToDeck(deckId: Long, front: String, back: String, contextText: String = ""): Long = withContext(Dispatchers.IO) {
        val card = FlashcardEntity(
            deckId = deckId,
            front = front,
            back = back,
            sourceContext = contextText,
            createdAt = System.currentTimeMillis()
        )
        val id = flashcardDao.insertFlashcard(card)
        deckDao.getDeckById(deckId)?.let {
            deckDao.updateDeck(it.copy(updatedAt = System.currentTimeMillis()))
        }
        id
    }

    suspend fun updateCard(card: FlashcardEntity) = withContext(Dispatchers.IO) {
        flashcardDao.updateFlashcard(card)
    }

    suspend fun deleteCard(cardId: Long) = withContext(Dispatchers.IO) {
        flashcardDao.deleteFlashcardById(cardId)
    }

    suspend fun deleteDeck(deckId: Long) = withContext(Dispatchers.IO) {
        deckDao.deleteDeckById(deckId)
    }

    suspend fun renameDeck(deckId: Long, newName: String) = withContext(Dispatchers.IO) {
        deckDao.getDeckById(deckId)?.let {
            deckDao.updateDeck(it.copy(name = newName, updatedAt = System.currentTimeMillis()))
        }
    }

    /**
     * Algoritmo de Repetición Espaciada SM-2 optimizado para Kardia.
     */
    suspend fun processCardReview(card: FlashcardEntity, rating: ReviewRating) = withContext(Dispatchers.IO) {
        val q = rating.quality // 1 a 4
        var repetitions = card.repetitions
        var intervalDays = card.intervalDays
        var easeFactor = card.easeFactor

        // Actualizar factor de facilidad (Ease Factor)
        easeFactor = max(1.3f, easeFactor + (0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f)))

        if (q < 2) {
            // Fallo (AGAIN)
            repetitions = 0
            intervalDays = 1
        } else {
            // Acierto (HARD, GOOD, EASY)
            repetitions += 1
            intervalDays = when (repetitions) {
                1 -> 1
                2 -> if (q == ReviewRating.EASY.quality) 4 else 2
                else -> (intervalDays * easeFactor).toInt()
            }
            if (q == ReviewRating.EASY.quality) {
                intervalDays = (intervalDays * 1.3f).toInt()
            }
        }

        val millisInDay = 24 * 60 * 60 * 1000L
        val nextReviewDate = System.currentTimeMillis() + (intervalDays * millisInDay)

        flashcardDao.updateReviewStats(
            cardId = card.id,
            repetitions = repetitions,
            intervalDays = intervalDays,
            easeFactor = easeFactor,
            nextReviewDate = nextReviewDate
        )
    }

    /**
     * Exporta un mazo de la base de datos a un archivo .apkg para Anki.
     */
    suspend fun exportDeckToApkg(deckId: Long): Result<File> = withContext(Dispatchers.IO) {
        try {
            val deckWithCards = deckDao.getDeckWithCards(deckId)
                ?: return@withContext Result.failure(Exception("Mazo no encontrado"))

            val drafts = deckWithCards.cards.map {
                DraftCard(
                    id = it.id.toString(),
                    front = it.front,
                    back = it.back,
                    sourceText = it.sourceContext
                )
            }

            val exportDir = File(context.filesDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val sanitizedDeckName = deckWithCards.deck.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val targetFile = File(exportDir, "$sanitizedDeckName.apkg")

            val compileResult = compiler.compile(drafts, deckWithCards.deck.name, targetFile)
            if (compileResult.isSuccess) {
                Result.success(targetFile)
            } else {
                Result.failure(compileResult.exceptionOrNull() ?: Exception("Error desconocido al exportar mazo."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
