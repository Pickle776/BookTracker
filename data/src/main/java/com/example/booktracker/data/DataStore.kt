package com.example.booktracker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

// --- DATA CLASS ---
data class Book(
    val title: String,
    val author: String,
    val language: String,
    val read: Boolean,
    val isYouth: Boolean = false,
    val isOnShelf: Boolean = false,
    val isNonFiction: Boolean = false
)

// DataStore delegate
val Context.dataStore by preferencesDataStore(name = "book_prefs")

// GLOBAL ENUM FOR SORTING
enum class SortOption {
    AUTHOR, TITLE, LANGUAGE
}

class BookDataStore(private val context: Context) {
    companion object {
        private val BOOKS_KEY = stringPreferencesKey("books_json")
        private val SORT_TITLE_KEY = booleanPreferencesKey("sort_title")
        private val SORT_OPTION_KEY = stringPreferencesKey("sort_option")
        private val FONT_SCALE_KEY = floatPreferencesKey("font_scale")
        private val LIST_TYPE = object : TypeToken<List<Book>>() {}.type

        // Existing Filters
        private val SHOW_READ_KEY = booleanPreferencesKey("filter_read")
        private val SHOW_UNREAD_KEY = booleanPreferencesKey("filter_unread")
        private val SELECTED_LANGUAGES_KEY = stringSetPreferencesKey("selected_languages")

        // New Tag Filters
        private val FILTER_YOUTH_KEY = booleanPreferencesKey("filter_youth_only")
        private val FILTER_OWNED_KEY = booleanPreferencesKey("filter_owned_only")
        private val FILTER_NONFICTION_KEY = booleanPreferencesKey("filter_nonfiction_only")

        private val FILTERS_INITIALIZED_KEY = booleanPreferencesKey("filters_initialized")

        private val CUSTOM_LANGUAGES_KEY = stringSetPreferencesKey("custom_languages")
        private val LAST_SELECTED_LANGUAGE_KEY = stringPreferencesKey("last_selected_language")
        private val LAST_READ_STATUS_KEY = booleanPreferencesKey("last_read_status")
        const val DEFAULT_LANGUAGE = "English"
    }

    private val gson = Gson()

    private fun normalizeLanguage(language: String): String {
        return language.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    fun getBooks(): Flow<List<Book>> =
        context.dataStore.data.map { prefs ->
            val json = prefs[BOOKS_KEY]
            if (json.isNullOrBlank()) {
                emptyList()
            } else {
                gson.fromJson<List<Book>>(json, LIST_TYPE) ?: emptyList()
            }
        }

    suspend fun saveBooks(list: List<Book>) {
        val json = gson.toJson(list)
        context.dataStore.edit { prefs ->
            prefs[BOOKS_KEY] = json
        }
    }

    suspend fun saveBook(book: Book) {
        context.dataStore.edit { prefs ->
            val currentJson = prefs[BOOKS_KEY]
            val currentList: MutableList<Book> = currentJson?.takeIf { it.isNotBlank() }?.let { nonBlankJson ->
                gson.fromJson<List<Book>>(nonBlankJson, LIST_TYPE)?.toMutableList() ?: mutableListOf()
            } ?: mutableListOf()

            currentList.add(book.copy(language = normalizeLanguage(book.language)))
            prefs[BOOKS_KEY] = gson.toJson(currentList)
        }
        saveLastSelectedLanguage(book.language)
        saveLastReadStatus(book.read)
    }

    // --- VIEW FILTERS (Read/Unread) ---
    fun getShowRead(): Flow<Boolean> = context.dataStore.data.map { it[SHOW_READ_KEY] ?: true }
    suspend fun saveShowRead(value: Boolean) { context.dataStore.edit { it[SHOW_READ_KEY] = value } }

    fun getShowUnread(): Flow<Boolean> = context.dataStore.data.map { it[SHOW_UNREAD_KEY] ?: true }
    suspend fun saveShowUnread(value: Boolean) { context.dataStore.edit { it[SHOW_UNREAD_KEY] = value } }

    // --- TAG FILTERS (Youth, Owned, Non-Fiction) ---
    fun getFilterYouth(): Flow<Boolean> = context.dataStore.data.map { it[FILTER_YOUTH_KEY] ?: false }
    suspend fun saveFilterYouth(value: Boolean) { context.dataStore.edit { it[FILTER_YOUTH_KEY] = value } }

    fun getFilterOwned(): Flow<Boolean> = context.dataStore.data.map { it[FILTER_OWNED_KEY] ?: false }
    suspend fun saveFilterOwned(value: Boolean) { context.dataStore.edit { it[FILTER_OWNED_KEY] = value } }

    fun getFilterNonFiction(): Flow<Boolean> = context.dataStore.data.map { it[FILTER_NONFICTION_KEY] ?: false }
    suspend fun saveFilterNonFiction(value: Boolean) { context.dataStore.edit { it[FILTER_NONFICTION_KEY] = value } }

    // --- LANGUAGE FILTERS ---
    fun getSelectedLanguages(): Flow<Set<String>> =
        context.dataStore.data.map { it[SELECTED_LANGUAGES_KEY] ?: emptySet() }

    suspend fun saveSelectedLanguages(languages: Set<String>) {
        context.dataStore.edit { it[SELECTED_LANGUAGES_KEY] = languages }
    }

    fun getFiltersInitialized(): Flow<Boolean> =
        context.dataStore.data.map { it[FILTERS_INITIALIZED_KEY] ?: false }

    suspend fun saveFilters(showRead: Boolean, showUnread: Boolean, languages: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[SHOW_READ_KEY] = showRead
            prefs[SHOW_UNREAD_KEY] = showUnread
            prefs[SELECTED_LANGUAGES_KEY] = languages
            prefs[FILTERS_INITIALIZED_KEY] = true
        }
    }

    // --- SORTING & SETTINGS ---
    fun getSortOption(): Flow<SortOption> =
        context.dataStore.data.map { prefs ->
            val savedString = prefs[SORT_OPTION_KEY]
            if (savedString != null) {
                try {
                    SortOption.valueOf(savedString)
                } catch (e: Exception) {
                    SortOption.AUTHOR
                }
            } else {
                val oldSortByTitle = prefs[SORT_TITLE_KEY] ?: false
                if (oldSortByTitle) SortOption.TITLE else SortOption.AUTHOR
            }
        }

    suspend fun saveSortOption(option: SortOption) {
        context.dataStore.edit {
            it[SORT_OPTION_KEY] = option.name
            it[SORT_TITLE_KEY] = (option == SortOption.TITLE)
        }
    }

    fun getFontScale(): Flow<Float> =
        context.dataStore.data.map { it[FONT_SCALE_KEY] ?: 1.3f }

    suspend fun saveFontScale(value: Float) {
        context.dataStore.edit { it[FONT_SCALE_KEY] = value }
    }

    // --- CUSTOM LANGUAGES MANAGEMENT ---
    fun getCustomLanguages(): Flow<Set<String>> =
        context.dataStore.data.map { prefs ->
            (prefs[CUSTOM_LANGUAGES_KEY] ?: emptySet()).map(::normalizeLanguage).toSet()
        }

    suspend fun addCustomLanguage(language: String) {
        context.dataStore.edit { prefs ->
            val currentLanguages = (prefs[CUSTOM_LANGUAGES_KEY] ?: emptySet()).map(::normalizeLanguage).toSet()
            prefs[CUSTOM_LANGUAGES_KEY] = currentLanguages + normalizeLanguage(language)
        }
    }

    suspend fun saveCustomLanguages(languages: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[CUSTOM_LANGUAGES_KEY] = languages.map(::normalizeLanguage).toSet()
        }
    }

    // --- PREVIOUS INPUT MEMORY ---
    fun getLastSelectedLanguage(): Flow<String> =
        context.dataStore.data.map { prefs ->
            prefs[LAST_SELECTED_LANGUAGE_KEY]?.let(::normalizeLanguage) ?: DEFAULT_LANGUAGE
        }

    suspend fun saveLastSelectedLanguage(language: String) {
        context.dataStore.edit { prefs ->
            prefs[LAST_SELECTED_LANGUAGE_KEY] = normalizeLanguage(language)
        }
    }

    fun getLastReadStatus(): Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[LAST_READ_STATUS_KEY] ?: false
        }

    suspend fun saveLastReadStatus(read: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[LAST_READ_STATUS_KEY] = read
        }
    }
}