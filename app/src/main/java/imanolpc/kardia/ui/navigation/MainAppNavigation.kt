package imanolpc.kardia.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import imanolpc.kardia.core.database.FlashcardEntity
import imanolpc.kardia.ui.generator.GeneratorScreen
import imanolpc.kardia.ui.generator.GeneratorViewModel
import imanolpc.kardia.ui.library.LibraryScreen
import imanolpc.kardia.ui.library.LibraryViewModel
import imanolpc.kardia.ui.study.DeckDetailScreen
import imanolpc.kardia.ui.study.StudySessionScreen
import kotlinx.coroutines.launch

enum class MainTab(val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    GENERATOR("Generador", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    LIBRARY("Mis Mazos", Icons.Filled.CollectionsBookmark, Icons.Outlined.CollectionsBookmark)
}

sealed interface NavDestination {
    object Main : NavDestination
    data class DeckDetail(val deckId: Long) : NavDestination
    data class Study(val deckId: Long, val deckName: String, val cards: List<FlashcardEntity>) : NavDestination
}

@Composable
fun MainAppNavigation(
    generatorViewModel: GeneratorViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(MainTab.GENERATOR) }
    var currentDestination by remember { mutableStateOf<NavDestination>(NavDestination.Main) }
    val scope = rememberCoroutineScope()

    val selectedDeckWithCards by libraryViewModel.selectedDeck.collectAsState()

    val availableModels by generatorViewModel.availableModels.collectAsState()
    val selectedModel by generatorViewModel.selectedModel.collectAsState()
    val downloadingModelId by generatorViewModel.downloadingModelId.collectAsState()
    val downloadProgress by generatorViewModel.downloadProgress.collectAsState()
    val downloadMessage by generatorViewModel.downloadMessage.collectAsState()
    val isSettingsOpen by generatorViewModel.isSettingsOpen.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (val dest = currentDestination) {
            is NavDestination.Main -> {
                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            MainTab.values().forEach { tab ->
                                val isSelected = currentTab == tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.title
                                        )
                                    },
                                    label = { Text(tab.title) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentTab) {
                            MainTab.GENERATOR -> {
                                GeneratorScreen(viewModel = generatorViewModel)
                            }
                            MainTab.LIBRARY -> {
                                LibraryScreen(
                                    viewModel = libraryViewModel,
                                    onOpenSettings = { generatorViewModel.openSettings() },
                                    onOpenDeckDetails = { deckId ->
                                        libraryViewModel.selectDeck(deckId)
                                        currentDestination = NavDestination.DeckDetail(deckId)
                                    },
                                    onStartStudy = { deckId ->
                                        scope.launch {
                                            val deckWithCards = generatorViewModel.deckRepository.getDeckWithCards(deckId)
                                            if (deckWithCards != null && deckWithCards.cards.isNotEmpty()) {
                                                val dueCards = generatorViewModel.deckRepository.getDueCards(deckId)
                                                currentDestination = NavDestination.Study(
                                                    deckId = deckId,
                                                    deckName = deckWithCards.deck.name,
                                                    cards = dueCards
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            is NavDestination.DeckDetail -> {
                DeckDetailScreen(
                    deckWithCards = selectedDeckWithCards,
                    onBack = { currentDestination = NavDestination.Main },
                    onStartStudy = {
                        selectedDeckWithCards?.let { deckWithCards ->
                            scope.launch {
                                val dueCards = generatorViewModel.deckRepository.getDueCards(deckWithCards.deck.id)
                                currentDestination = NavDestination.Study(
                                    deckId = deckWithCards.deck.id,
                                    deckName = deckWithCards.deck.name,
                                    cards = dueCards
                                )
                            }
                        }
                    },
                    onAddCard = { front, back ->
                        libraryViewModel.addCard(dest.deckId, front, back)
                    },
                    onDeleteCard = { cardId ->
                        libraryViewModel.deleteCard(cardId, dest.deckId)
                    }
                )
            }

            is NavDestination.Study -> {
                StudySessionScreen(
                    deckName = dest.deckName,
                    cards = dest.cards,
                    onRateCard = { card, rating ->
                        generatorViewModel.deckRepository.processCardReview(card, rating)
                    },
                    onFinishStudy = {
                        currentDestination = NavDestination.Main
                    }
                )
            }
        }

        // Diálogo global de configuración de modelos
        if (isSettingsOpen) {
            imanolpc.kardia.ui.settings.ModelSettingsDialog(
                models = availableModels,
                selectedModel = selectedModel,
                downloadingModelId = downloadingModelId,
                downloadProgress = downloadProgress,
                downloadMessage = downloadMessage,
                isModelDownloaded = { model -> generatorViewModel.llmManager.isModelDownloaded(model) },
                onSelectModel = { model -> generatorViewModel.selectModel(model) },
                onDownloadModel = { model -> generatorViewModel.downloadModel(model) },
                onDeleteModel = { model -> generatorViewModel.deleteModel(model) },
                onImportLocalFile = { uri, name -> generatorViewModel.importLocalModel(uri, name) },
                onRefreshCatalog = { generatorViewModel.loadCatalogAndCheckModel() },
                onDismissRequest = { generatorViewModel.closeSettings() }
            )
        }
    }
}
