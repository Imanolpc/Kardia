package imanolpc.kardia.core.database

import androidx.room.Embedded
import androidx.room.Relation

data class DeckWithCards(
    @Embedded
    val deck: DeckEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "deckId"
    )
    val cards: List<FlashcardEntity>
)

data class DeckWithCardCount(
    @Embedded
    val deck: DeckEntity,
    val cardCount: Int,
    val dueCount: Int
)
