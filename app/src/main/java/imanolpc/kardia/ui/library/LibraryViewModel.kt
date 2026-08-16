package imanolpc.kardia.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import imanolpc.kardia.core.database.DeckEntity
import imanolpc.kardia.core.database.DeckRepository
import imanolpc.kardia.core.database.DeckWithCardCount
import imanolpc.kardia.core.database.DeckWithCards
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DeckRepository(application)

    val decks: StateFlow<List<DeckWithCardCount>> = repository.getAllDecksWithStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDeck = MutableStateFlow<DeckWithCards?>(null)
    val selectedDeck: StateFlow<DeckWithCards?> = _selectedDeck.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun selectDeck(deckId: Long) {
        viewModelScope.launch {
            val deckWithCards = repository.getDeckWithCards(deckId)
            _selectedDeck.value = deckWithCards
        }
    }

    fun clearSelectedDeck() {
        _selectedDeck.value = null
    }

    fun createDeck(name: String) {
        viewModelScope.launch {
            repository.saveDeckWithDraftCards(name, emptyList())
        }
    }

    fun renameDeck(deckId: Long, newName: String) {
        viewModelScope.launch {
            repository.renameDeck(deckId, newName)
            if (_selectedDeck.value?.deck?.id == deckId) {
                selectDeck(deckId)
            }
        }
    }

    fun deleteDeck(deckId: Long) {
        viewModelScope.launch {
            repository.deleteDeck(deckId)
            if (_selectedDeck.value?.deck?.id == deckId) {
                _selectedDeck.value = null
            }
        }
    }

    fun addCard(deckId: Long, front: String, back: String, context: String = "") {
        viewModelScope.launch {
            repository.addCardToDeck(deckId, front, back, context)
            selectDeck(deckId)
        }
    }

    fun deleteCard(cardId: Long, deckId: Long) {
        viewModelScope.launch {
            repository.deleteCard(cardId)
            selectDeck(deckId)
        }
    }

    fun exportDeck(deckId: Long, onResult: (Result<File>) -> Unit) {
        viewModelScope.launch {
            val result = repository.exportDeckToApkg(deckId)
            onResult(result)
        }
    }
}
