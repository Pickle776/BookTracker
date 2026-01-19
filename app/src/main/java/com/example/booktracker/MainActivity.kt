package com.example.booktracker

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.booktracker.data.Book
import com.example.booktracker.data.BookDataStore
import com.example.booktracker.data.BookDataStore.Companion.DEFAULT_LANGUAGE
import com.example.booktracker.data.SortOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MainActivity.kt
 * Single activity app using Jetpack Compose. Relies on BookDataStore (DataStore.kt) for persistence.
 */

// --- CUSTOM DARK THEME COLORS ---
private val DarkBackground = Color(0xFF121212)
private val DarkSurface = Color(0xFF1E1E1E)
private val DarkSurfaceVariant = Color(0xFF2C2C2C)
private val SteelBluePrimary = Color(0xFF6D98BA)
private val SteelBlueDark = Color(0xFF456885)
private val TextWhite = Color(0xFFE6E6E6)
private val TextGrey = Color(0xFFAAAAAA)
private val OutlineGrey = Color(0xFF444444)

private val BookTrackerDarkScheme = darkColorScheme(
    primary = SteelBluePrimary,
    onPrimary = Color(0xFF121212),
    primaryContainer = SteelBlueDark,
    onPrimaryContainer = TextWhite,
    background = DarkBackground,
    onBackground = TextWhite,
    surface = DarkSurface,
    onSurface = TextWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextGrey,
    outline = OutlineGrey,
    outlineVariant = OutlineGrey,
    error = Color(0xFFCF6679),
    onError = Color.Black
)

@Composable
fun BookTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BookTrackerDarkScheme,
        content = content
    )
}

class MainActivity : ComponentActivity() {
    private lateinit var dataStore: BookDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Lock orientation to Portrait
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // 2. FIX: Force the System Bars (Top Status & Bottom Nav) to match the App Background
        window.statusBarColor = android.graphics.Color.parseColor("#121212")
        window.navigationBarColor = android.graphics.Color.parseColor("#121212")

        dataStore = BookDataStore(applicationContext)

        setContent {
            BookTrackerTheme {
                AppRoot(dataStore)
            }
        }
    }
}

@Composable
fun AppRoot(dataStore: BookDataStore) {
    val scope = rememberCoroutineScope()
    val books by dataStore.getBooks().collectAsState(initial = emptyList())
    val sortOption by dataStore.getSortOption().collectAsState(initial = SortOption.AUTHOR)
    val fontScale by dataStore.getFontScale().collectAsState(initial = 1.3f)
    val showRead by dataStore.getShowRead().collectAsState(initial = true)
    val showUnread by dataStore.getShowUnread().collectAsState(initial = true)

    // Tag Filters
    val filterYouth by dataStore.getFilterYouth().collectAsState(initial = false)
    val filterOwned by dataStore.getFilterOwned().collectAsState(initial = false)
    val filterNonFiction by dataStore.getFilterNonFiction().collectAsState(initial = false)

    val selectedLanguages by dataStore.getSelectedLanguages().collectAsState(initial = emptySet())
    val filtersInitialized by dataStore.getFiltersInitialized().collectAsState(initial = false)

    // --- SNACKBAR & TAP LOGIC ---
    val snackbarHostState = remember { SnackbarHostState() }
    var readToggleClickCount by remember { mutableIntStateOf(0) }
    var readToggleResetJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(books, filtersInitialized) {
        // 1. Initialize filters if needed
        if (!filtersInitialized && books.isNotEmpty()) {
            val allLanguages = books.map { it.language }.distinct().toSet()
            dataStore.saveFilters(showRead = true, showUnread = true, languages = allLanguages)
        }

        // 2. AUTO-DELETE UNUSED LANGUAGES
        val standardLanguages = setOf("English", "Afrikaans")
        val usedCustomLanguages = books.map { it.language }
            .filter { !standardLanguages.contains(it) }
            .toSet()

        dataStore.saveCustomLanguages(usedCustomLanguages)
    }

    val navController = rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        NavHost(navController = navController, startDestination = "main") {
            composable("main") {
                MainScreen(
                    books = books,
                    sortOption = sortOption,
                    fontScale = fontScale,
                    showRead = showRead,
                    showUnread = showUnread,
                    filterYouth = filterYouth,
                    filterOwned = filterOwned,
                    filterNonFiction = filterNonFiction,
                    selectedLanguages = selectedLanguages,
                    snackbarHostState = snackbarHostState, // Recieve Snackbar State
                    onSetSort = { newOption -> scope.launch { dataStore.saveSortOption(newOption) } },
                    onSetFontScale = { newScale -> scope.launch { dataStore.saveFontScale(newScale) } },
                    onToggleShowRead = { scope.launch { dataStore.saveShowRead(!showRead) } },
                    onToggleShowUnread = { scope.launch { dataStore.saveShowUnread(!showUnread) } },
                    onToggleFilterYouth = { scope.launch { dataStore.saveFilterYouth(!filterYouth) } },
                    onToggleFilterOwned = { scope.launch { dataStore.saveFilterOwned(!filterOwned) } },
                    onToggleFilterNonFiction = { scope.launch { dataStore.saveFilterNonFiction(!filterNonFiction) } },
                    onToggleLanguage = { lang ->
                        val newSet = if (selectedLanguages.contains(lang)) {
                            selectedLanguages - lang
                        } else {
                            selectedLanguages + lang
                        }
                        scope.launch { dataStore.saveSelectedLanguages(newSet) }
                    },
                    onAdd = { navController.navigate("add") },
                    onToggleBookRead = { book, newRead ->
                        // 1. Normal Save Logic
                        val updated = books.map { if (it == book) it.copy(read = newRead) else it }
                        scope.launch { dataStore.saveBooks(updated) }

                        // 2. Frantic Tap Detection
                        readToggleClickCount++
                        readToggleResetJob?.cancel() // Reset timer

                        // If user tapped 3 times quickly
                        if (readToggleClickCount >= 3) {
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss() // Dismiss old if distinct
                                snackbarHostState.showSnackbar(
                                    message = "Checkbox marks as Read/Unread. Long press to Delete.",
                                    duration = SnackbarDuration.Short,
                                    withDismissAction = true
                                )
                            }
                            readToggleClickCount = 0
                        }

                        // Reset counter after 1 second of inactivity
                        readToggleResetJob = scope.launch {
                            delay(1000)
                            readToggleClickCount = 0
                        }
                    },
                    onToggleBookOwned = { book, newOwned ->
                        val updated = books.map { if (it == book) it.copy(isOnShelf = newOwned) else it }
                        scope.launch { dataStore.saveBooks(updated) }
                    },
                    onDeleteBook = { bookToDelete ->
                        val updated = books.filter { it != bookToDelete }
                        scope.launch { dataStore.saveBooks(updated) }
                    }
                )
            }

            composable("add") {
                AddBookScreen(
                    books = books,
                    dataStore = dataStore,
                    onSave = { book ->
                        scope.launch {
                            val currentSelected = selectedLanguages.toMutableSet()
                            currentSelected.add(book.language)
                            dataStore.saveSelectedLanguages(currentSelected)
                            dataStore.saveBook(book)
                        }
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() },
                    fontScale = fontScale
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    books: List<Book>,
    sortOption: SortOption,
    fontScale: Float,
    showRead: Boolean,
    showUnread: Boolean,
    filterYouth: Boolean,
    filterOwned: Boolean,
    filterNonFiction: Boolean,
    selectedLanguages: Set<String>,
    snackbarHostState: SnackbarHostState, // Recieve Snackbar State
    onSetSort: (SortOption) -> Unit,
    onSetFontScale: (Float) -> Unit,
    onToggleShowRead: () -> Unit,
    onToggleShowUnread: () -> Unit,
    onToggleFilterYouth: () -> Unit,
    onToggleFilterOwned: () -> Unit,
    onToggleFilterNonFiction: () -> Unit,
    onToggleLanguage: (String) -> Unit,
    onAdd: () -> Unit,
    onToggleBookRead: (Book, Boolean) -> Unit,
    onToggleBookOwned: (Book, Boolean) -> Unit,
    onDeleteBook: (Book) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }

    val fontMul = fontScale
    val fixedButtonHeight = 52.dp
    val buttonRowEffectiveHeight = fixedButtonHeight + 64.dp

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }

    var highlightedBookKey by remember { mutableStateOf<String?>(null) }
    val itemCoordinates = remember { mutableStateMapOf<String, Rect>() }

    BackHandler(enabled = highlightedBookKey != null || searchVisible) {
        if (highlightedBookKey != null) {
            highlightedBookKey = null
        } else if (searchVisible) {
            search = ""
            searchVisible = false
        }
    }

    val availableLanguages = remember(books) {
        books.map { it.language }.distinct().sorted()
    }

    val filtered = remember(books, search, sortOption, showRead, showUnread, filterYouth, filterOwned, filterNonFiction, selectedLanguages) {
        books.filter { b ->
            val matchesSearch = search.isBlank() ||
                    b.title.contains(search, ignoreCase = true) ||
                    b.author.contains(search, ignoreCase = true)

            val matchesReadStatus = (b.read && showRead) || (!b.read && showUnread)

            // Inclusive OR logic for tags
            val isAnyTagFilterOn = filterYouth || filterOwned || filterNonFiction
            val matchesTags = if (!isAnyTagFilterOn) {
                true
            } else {
                (filterYouth && b.isYouth) ||
                        (filterOwned && b.isOnShelf) ||
                        (filterNonFiction && b.isNonFiction)
            }

            val matchesLanguage = selectedLanguages.contains(b.language)

            matchesSearch && matchesReadStatus && matchesTags && matchesLanguage
        }.let { list ->
            when (sortOption) {
                SortOption.TITLE -> list.sortedBy { it.title.lowercase() }
                SortOption.LANGUAGE -> list.sortedBy { it.language.lowercase() }
                SortOption.AUTHOR -> list.sortedBy {
                    val a = it.author.trim()
                    when {
                        a.contains(",") -> a.substringBefore(",").trim().lowercase()
                        a.contains(" ") -> a.split(Regex("\\s+")).lastOrNull()?.lowercase() ?: a.lowercase()
                        else -> a.lowercase()
                    }
                }
            }
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. SORT BY
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Sort By", fontScale)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UnifiedFilterChip("Author", sortOption == SortOption.AUTHOR, { onSetSort(SortOption.AUTHOR) }, fontScale)
                        UnifiedFilterChip("Title", sortOption == SortOption.TITLE, { onSetSort(SortOption.TITLE) }, fontScale)
                        UnifiedFilterChip("Language", sortOption == SortOption.LANGUAGE, { onSetSort(SortOption.LANGUAGE) }, fontScale)
                    }
                }

                // 2. FONT SIZE
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Font Size", fontScale)
                    val options = listOf(Triple("Small", 1.0f, 1.0f), Triple("Medium", 1.3f, 1.3f), Triple("Large", 1.6f, 1.6f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        options.forEachIndexed { index, option ->
                            val isSelected = fontScale == option.second
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { onSetFontScale(option.second) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option.first,
                                    fontSize = (13.sp * option.third),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 3. STATUS (Read/Unread)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Filter by Status", fontScale)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UnifiedFilterChip("Read", showRead, onToggleShowRead, fontScale)
                        UnifiedFilterChip("Unread", showUnread, onToggleShowUnread, fontScale)
                    }
                }

                // 4. TAGS (Youth, Owned, Non-Fiction)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        SectionHeader("Filter by Tags", fontScale)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "(Select none to show all)",
                            fontSize = (10.sp * fontScale),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UnifiedFilterChip("Youth", filterYouth, onToggleFilterYouth, fontScale)
                        UnifiedFilterChip("Owned", filterOwned, onToggleFilterOwned, fontScale)
                        UnifiedFilterChip("Non-Fiction", filterNonFiction, onToggleFilterNonFiction, fontScale)
                    }
                }

                // 5. LANGUAGES
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Filter by Language", fontScale)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (availableLanguages.isNotEmpty()) {
                            availableLanguages.forEach { lang ->
                                UnifiedFilterChip(lang, selectedLanguages.contains(lang), { onToggleLanguage(lang) }, fontScale)
                            }
                        }
                    }
                    if (availableLanguages.isEmpty()) {
                        Text("No languages found.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = (11.sp * fontScale), modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    LaunchedEffect(searchVisible) {
        if (searchVisible) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(highlightedBookKey) {
        if (highlightedBookKey != null) {
            focusManager.clearFocus()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        if (search.isEmpty()) {
                            searchVisible = false
                        }
                    })
                },
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showBottomSheet = true }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Menu, "Menu", tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(12.dp))
                            Text("Book Tracker", fontSize = (22.sp * fontMul), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onBackground, actionIconContentColor = MaterialTheme.colorScheme.onBackground),
                    actions = {
                        IconButton(onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) search = ""
                        }) { Icon(Icons.Filled.Search, "Search") }
                    }
                )
            },
            // ADDED SNACKBAR HOST HERE
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.navigationBarsPadding() // Ensures it sits above nav bar
                ) {
                    Snackbar(
                        snackbarData = it,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        actionColor = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                AnimatedVisibility(visible = searchVisible) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search...", fontSize = (12.sp * fontMul)) },
                        textStyle = LocalTextStyle.current.copy(fontSize = (14.sp * fontMul), color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focusRequester),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (search.isNotEmpty()) {
                                IconButton(onClick = { search = "" }) { Icon(Icons.Filled.Clear, "Clear search") }
                            }
                        }
                    )
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    val topListPadding = 0.dp
                    val bottomListPadding = buttonRowEffectiveHeight + 8.dp

                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(bottom = buttonRowEffectiveHeight),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (search.isNotEmpty()) "No results found" else "No books in library",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = (14.sp * fontMul),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        userScrollEnabled = highlightedBookKey == null,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topListPadding, bottom = bottomListPadding)
                    ) {
                        items(items = filtered, key = { book -> "${book.title}-${book.author}" }) { book ->
                            val bookKey = "${book.title}-${book.author}"
                            BookItem(
                                book = book,
                                fontMul = fontMul,
                                isHighlighted = false,
                                onToggleRead = { isChecked -> onToggleBookRead(book, isChecked) },
                                onToggleOwned = { },
                                onDelete = { },
                                modifier = Modifier
                                    .animateItem()
                                    .onGloballyPositioned { layoutCoordinates ->
                                        if (layoutCoordinates.isAttached) {
                                            // Store coordinates using stable String key
                                            itemCoordinates[bookKey] = Rect(layoutCoordinates.positionInRoot(), layoutCoordinates.size.toSize())
                                        }
                                    }
                                    .pointerInput(book) {
                                        detectTapGestures(onLongPress = { highlightedBookKey = bookKey })
                                    }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 32.dp, vertical = 32.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onAdd,
                            modifier = Modifier.height(fixedButtonHeight),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) { Text("+ Add Book", fontSize = (13.sp * fontMul)) }
                    }
                }
            }
        }

        // --- OVERLAY LOGIC ---
        if (highlightedBookKey != null) {
            // Find the most recent version of this book from the list using the stable ID
            val activeBook = books.find { "${it.title}-${it.author}" == highlightedBookKey }

            // If book was DELETED (returns null), then close the overlay.
            if (activeBook == null) {
                highlightedBookKey = null
            } else {
                // Book exists. Check if we have coordinates.
                val rect = itemCoordinates[highlightedBookKey]

                // If coordinates exist, draw the overlay.
                // If coordinates are null (rare frame skip), we do nothing and wait for them to reappear.
                if (rect != null) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).pointerInput(Unit) { detectTapGestures(onTap = { highlightedBookKey = null }) })

                    Box(modifier = Modifier.fillMaxWidth().offset { IntOffset(0, rect.top.toInt()) }) {
                        BookItem(
                            book = activeBook,
                            fontMul = fontMul,
                            isHighlighted = true,
                            onToggleRead = { },
                            onToggleOwned = { newOwned ->
                                onToggleBookOwned(activeBook, newOwned)
                            },
                            onDelete = {
                                onDeleteBook(activeBook)
                                highlightedBookKey = null
                            },
                            modifier = Modifier.clickable(enabled = false) {}
                        )
                    }
                }
            }
        }
    }
}

// --- HELPER COMPOSABLE FUNCTIONS ---

@Composable
fun SectionHeader(text: String, fontScale: Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontSize = (13.sp * fontScale),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedFilterChip(label: String, selected: Boolean, onClick: () -> Unit, fontScale: Float) {
    FilterChip(
        modifier = Modifier.height(36.dp),
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = (13.sp * fontScale), fontWeight = FontWeight.Medium) },
        leadingIcon = {
            Icon(
                imageVector = if (selected) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        shape = RoundedCornerShape(18.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = Color.Transparent,
            borderWidth = 1.dp
        )
    )
}

@Composable
fun StatusBadge(text: String, fontMul: Float, isInteractive: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(6.dp)
    val padding = PaddingValues(horizontal = 5.dp, vertical = 1.dp)

    val modifier = if (isInteractive && onClick != null) {
        Modifier
            .shadow(elevation = 4.dp, shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable { onClick() }
            .padding(padding)
    } else {
        Modifier
            .clip(shape)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.primary, shape = shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(padding)
    }

    Box(modifier = modifier) {
        Text(
            text = text,
            color = if (isInteractive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            fontSize = (8.sp * fontMul),
            fontWeight = FontWeight.Bold
        )
    }
}

// --- WATERMARK COMPONENT ---
@Composable
fun WatermarkPreview(content: @Composable () -> Unit) {
    Box(modifier = Modifier.clip(RoundedCornerShape(12.dp))) {
        // Background Watermark
        Box(
            modifier = Modifier
                .matchParentSize()
                .alpha(0.06f) // Faint opacity
                .rotate(-30f), // Angle
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Repeating "PREVIEW" text
                repeat(4) {
                    Text(
                        text = "PREVIEW   PREVIEW   PREVIEW",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Visible,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        // Actual Content
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookItem(
    book: Book,
    fontMul: Float,
    isHighlighted: Boolean,
    onToggleRead: (Boolean) -> Unit,
    onToggleOwned: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val outerPaddingValue = (8.dp * fontMul)
    val verticalOuterPad = if (isHighlighted) 0.dp else outerPaddingValue
    val shape = if (isHighlighted) RectangleShape else MaterialTheme.shapes.small
    val horizontalInnerPad = if (isHighlighted) 24.dp else 8.dp
    val extraPad = if (isHighlighted) outerPaddingValue else 0.dp
    val verticalInnerPad = 8.dp + extraPad
    val backgroundColor = if (isHighlighted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val elevation = if (isHighlighted) 8.dp else 0.dp

    val displayAuthor = remember(book.author) {
        if (book.author.contains(",")) {
            val parts = book.author.split(",")
            if (parts.size >= 2) "${parts[1].trim()} ${parts[0].trim()}" else book.author
        } else {
            book.author
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth().padding(vertical = verticalOuterPad),
        shape = shape,
        color = backgroundColor,
        tonalElevation = elevation,
        shadowElevation = elevation
    ) {
        Row(
            modifier = Modifier.padding(start = horizontalInnerPad, end = horizontalInnerPad, top = verticalInnerPad, bottom = verticalInnerPad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isHighlighted) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete Book", tint = MaterialTheme.colorScheme.error) }
            } else {
                Checkbox(checked = book.read, onCheckedChange = onToggleRead, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary, uncheckedColor = MaterialTheme.colorScheme.outline))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, fontWeight = FontWeight.Bold, fontSize = (15.sp * fontMul), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                // AUTHOR ROW with BADGES
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(displayAuthor, fontSize = (13.sp * fontMul), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (book.isYouth) StatusBadge("YTH", fontMul)
                    if (book.isNonFiction) StatusBadge("N-F", fontMul)

                    // OWN badge - either interactive (highlighted) or static (normal)
                    if (isHighlighted) {
                        // Interactive version with +/- prefix
                        val badgeText = if (book.isOnShelf) "− OWN" else "+ OWN"
                        StatusBadge(
                            text = badgeText,
                            fontMul = fontMul,
                            isInteractive = true,
                            onClick = { onToggleOwned(!book.isOnShelf) }
                        )
                    } else {
                        // Static version - only show if owned
                        if (book.isOnShelf) {
                            StatusBadge("OWN", fontMul)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CleanTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    fontMul: Float
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(label, fontSize = (13.sp * fontMul)) },
        textStyle = LocalTextStyle.current.copy(fontSize = (15.sp * fontMul), color = MaterialTheme.colorScheme.onSurface),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddBookScreen(
    books: List<Book>,
    dataStore: BookDataStore,
    onSave: (Book) -> Unit,
    onCancel: () -> Unit,
    fontScale: Float
) {
    val scope = rememberCoroutineScope()
    // Form state
    var surname by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("") }
    var newLanguageInput by remember { mutableStateOf("") }

    var showError by remember { mutableStateOf(false) }
    var showLanguageInput by remember { mutableStateOf(false) }

    // Two-stage flow: true = input fields, false = chips + language + buttons
    var showingInputs by remember { mutableStateOf(true) }

    // Chip states
    var read by remember { mutableStateOf(false) }
    var isYouth by remember { mutableStateOf(false) }
    var isOnShelf by remember { mutableStateOf(false) }
    var isNonFiction by remember { mutableStateOf(false) }

    val customLanguages by dataStore.getCustomLanguages().collectAsState(initial = emptySet())
    val lastSelectedLanguage by dataStore.getLastSelectedLanguage().collectAsState(initial = DEFAULT_LANGUAGE)

    // Set initial language ONCE when screen loads
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(lastSelectedLanguage) {
        if (!initialized && selectedLanguage.isEmpty()) {
            selectedLanguage = lastSelectedLanguage
            initialized = true
        }
    }

    val fontMul = fontScale

    val standardLanguages = listOf("English", "Afrikaans")
    val allLanguages = remember(customLanguages) {
        (standardLanguages + customLanguages.toList()).distinct().sorted()
    }

    val isFormValid by remember(title, surname) {
        derivedStateOf { title.isNotBlank() && surname.isNotBlank() }
    }

    val isNewLanguageValid by remember(newLanguageInput) {
        derivedStateOf { newLanguageInput.isNotBlank() }
    }

    val focusRequester = remember { FocusRequester() }
    val languageFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    BackHandler {
        if (showLanguageInput) {
            showLanguageInput = false
        } else if (!showingInputs) {
            showingInputs = true
        } else {
            onCancel()
        }
    }

    LaunchedEffect(showingInputs) {
        if (showingInputs) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(showLanguageInput) {
        if (showLanguageInput) {
            languageFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        if (showingInputs) {
            // STAGE 1: Input Fields with Keyboard (3 fields only)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(Modifier.height(24.dp))

                Text(
                    "Add New Book",
                    fontSize = (22.sp * fontMul),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // REDUCED SPACER
                Spacer(Modifier.height(16.dp))

                // SURNAME HEADER
                Text(
                    text = "Author's Surname",
                    fontSize = (11.sp * fontMul),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                CleanTextField(
                    value = surname,
                    onValueChange = { surname = it; showError = false },
                    label = "Author's Surname",
                    modifier = Modifier.focusRequester(focusRequester),
                    fontMul = fontMul
                )

                // REVERTED SPACER
                Spacer(Modifier.height(16.dp))

                // NAME HEADER
                Text(
                    text = "Author's Name (Optional)",
                    fontSize = (11.sp * fontMul),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                CleanTextField(
                    value = name,
                    onValueChange = { name = it; showError = false },
                    label = "Author's Name",
                    fontMul = fontMul
                )

                // REVERTED SPACER
                Spacer(Modifier.height(16.dp))

                // TITLE HEADER
                Text(
                    text = "Book Title",
                    fontSize = (11.sp * fontMul),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                CleanTextField(
                    value = title,
                    onValueChange = { title = it; showError = false },
                    label = "Book Title",
                    fontMul = fontMul
                )

                if (showError) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "This book already exists.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = (13.sp * fontMul)
                    )
                }

                Spacer(Modifier.weight(1f))

                // Two Buttons: Cancel and Continue -> Fixed to corners with 12dp padding
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .width(130.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                        )
                    ) {
                        Text("Cancel", fontSize = (14.sp * fontMul))
                    }

                    Button(
                        onClick = {
                            val author = if (name.isNotBlank()) "$surname, $name" else surname
                            val bookExists = books.any {
                                it.title.equals(title, ignoreCase = true) &&
                                        it.author.equals(author, ignoreCase = true)
                            }

                            if (bookExists) {
                                showError = true
                            } else {
                                showingInputs = false
                            }
                        },
                        enabled = isFormValid,
                        modifier = Modifier
                            .width(130.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Continue", fontSize = (14.sp * fontMul), fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            // STAGE 2: Live Preview + Tags + Language + Actions
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Spacer(Modifier.height(24.dp))

                    Text(
                        "Add New Book",
                        fontSize = (22.sp * fontMul),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(24.dp))

                    // Live Preview Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        // WRAPPED PREVIEW CONTENT IN WATERMARK
                        WatermarkPreview {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Unclickable checkbox showing read status
                                Checkbox(
                                    checked = read,
                                    onCheckedChange = null,
                                    enabled = false,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        uncheckedColor = MaterialTheme.colorScheme.outline,
                                        disabledCheckedColor = MaterialTheme.colorScheme.primary,
                                        disabledUncheckedColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        title,
                                        fontSize = (16.sp * fontMul),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val displayAuthor =
                                        if (name.isNotBlank()) "$name $surname" else surname
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            displayAuthor,
                                            fontSize = (14.sp * fontMul),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (isYouth) StatusBadge("YTH", fontMul)
                                        if (isOnShelf) StatusBadge("OWN", fontMul)
                                        if (isNonFiction) StatusBadge("N-F", fontMul)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    Text(
                        "Tags",
                        fontSize = (11.sp * fontMul),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UnifiedFilterChip("Read", read, { read = !read }, fontMul)
                        UnifiedFilterChip("Youth", isYouth, { isYouth = !isYouth }, fontMul)
                        UnifiedFilterChip("Owned", isOnShelf, { isOnShelf = !isOnShelf }, fontMul)
                        UnifiedFilterChip("Non-Fiction", isNonFiction, { isNonFiction = !isNonFiction }, fontMul)
                    }

                    Spacer(Modifier.height(32.dp))

                    Text(
                        "Language",
                        fontSize = (11.sp * fontMul),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (!showLanguageInput) {
                        // Show selected language or selector
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            allLanguages.forEach { lang ->
                                UnifiedFilterChip(
                                    lang,
                                    selectedLanguage == lang,
                                    { selectedLanguage = lang },
                                    fontMul
                                )
                            }
                            // Add new language button
                            OutlinedButton(
                                onClick = {
                                    newLanguageInput = "" // Start clean
                                    showLanguageInput = true
                                },
                                modifier = Modifier.height(36.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add language",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("New", fontSize = (13.sp * fontMul), fontWeight = FontWeight.Medium)
                            }
                        }
                    } else {
                        // Show text input for new language
                        Column {
                            CleanTextField(
                                value = newLanguageInput,
                                onValueChange = { newLanguageInput = it },
                                label = "Enter Language",
                                fontMul = fontMul,
                                modifier = Modifier.focusRequester(languageFocusRequester)
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        showLanguageInput = false
                                    },
                                    modifier = Modifier.height(36.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                                    )
                                ) {
                                    Text("Cancel", fontSize = (13.sp * fontMul))
                                }
                                Button(
                                    onClick = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()

                                        val formatted = newLanguageInput.trim().replaceFirstChar { it.uppercase() }
                                        if (formatted.isNotEmpty()) {
                                            // IMMEDIATE SAVE to DataStore
                                            scope.launch {
                                                dataStore.addCustomLanguage(formatted)
                                            }
                                            selectedLanguage = formatted
                                        }
                                        showLanguageInput = false
                                    },
                                    enabled = isNewLanguageValid,
                                    modifier = Modifier.height(36.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text("Confirm", fontSize = (13.sp * fontMul), fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                // Bottom Buttons - only show when not in language input mode
                if (!showLanguageInput) {
                    Column {
                        // Fixed to corners with 12dp padding
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 32.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(
                                onClick = { showingInputs = true },
                                modifier = Modifier
                                    .width(130.dp)
                                    .height(52.dp),
                                shape = RoundedCornerShape(26.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                                )
                            ) {
                                Text("Back", fontSize = (14.sp * fontMul))
                            }

                            Button(
                                onClick = {
                                    val author = if (name.isNotBlank()) "$surname, $name" else surname
                                    val formattedLanguage = selectedLanguage.trim().replaceFirstChar { it.uppercase() }

                                    onSave(
                                        Book(
                                            title = title,
                                            author = author,
                                            language = formattedLanguage,
                                            read = read,
                                            isYouth = isYouth,
                                            isOnShelf = isOnShelf,
                                            isNonFiction = isNonFiction
                                        )
                                    )
                                },
                                enabled = true, // Simplified check since selectedLanguage is safer now
                                modifier = Modifier
                                    .width(130.dp)
                                    .height(52.dp),
                                shape = RoundedCornerShape(26.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Add Book", fontSize = (14.sp * fontMul), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}