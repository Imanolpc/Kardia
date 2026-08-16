package imanolpc.kardia.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    @Query("SELECT * FROM decks ORDER BY updatedAt DESC")
    fun getAllDecks(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE id = :id")
    fun getDeckById(id: Long): DeckEntity?

    @Transaction
    @Query("SELECT * FROM decks WHERE id = :id")
    fun getDeckWithCardsFlow(id: Long): Flow<DeckWithCards?>

    @Transaction
    @Query("SELECT * FROM decks WHERE id = :id")
    fun getDeckWithCards(id: Long): DeckWithCards?

    @Query("""
        SELECT d.*, 
               COUNT(f.id) AS cardCount,
               SUM(CASE WHEN f.nextReviewDate <= :currentTime THEN 1 ELSE 0 END) AS dueCount
        FROM decks d
        LEFT JOIN flashcards f ON d.id = f.deckId
        GROUP BY d.id
        ORDER BY d.updatedAt DESC
    """)
    fun getDecksWithStats(currentTime: Long = System.currentTimeMillis()): Flow<List<DeckWithCardCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDeck(deck: DeckEntity): Long

    @Update
    fun updateDeck(deck: DeckEntity)

    @Delete
    fun deleteDeck(deck: DeckEntity)

    @Query("DELETE FROM decks WHERE id = :deckId")
    fun deleteDeckById(deckId: Long)
}
