package imanolpc.kardia.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId ORDER BY id ASC")
    fun getCardsForDeckFlow(deckId: Long): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId ORDER BY id ASC")
    fun getCardsForDeck(deckId: Long): List<FlashcardEntity>

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId AND nextReviewDate <= :currentTime ORDER BY nextReviewDate ASC")
    fun getDueCardsForDeck(deckId: Long, currentTime: Long = System.currentTimeMillis()): List<FlashcardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFlashcard(card: FlashcardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFlashcards(cards: List<FlashcardEntity>): List<Long>

    @Update
    fun updateFlashcard(card: FlashcardEntity)

    @Delete
    fun deleteFlashcard(card: FlashcardEntity)

    @Query("DELETE FROM flashcards WHERE id = :cardId")
    fun deleteFlashcardById(cardId: Long)

    @Query("""
        UPDATE flashcards 
        SET repetitions = :repetitions,
            intervalDays = :intervalDays,
            easeFactor = :easeFactor,
            nextReviewDate = :nextReviewDate
        WHERE id = :cardId
    """)
    fun updateReviewStats(
        cardId: Long,
        repetitions: Int,
        intervalDays: Int,
        easeFactor: Float,
        nextReviewDate: Long
    )
}
