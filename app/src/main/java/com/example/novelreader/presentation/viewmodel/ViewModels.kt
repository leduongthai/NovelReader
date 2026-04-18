package com.example.novelreader.presentation.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.novelreader.data.remote.crawler.CrawlerConfig
import com.example.novelreader.data.remote.crawler.CrawlerConfigs
import com.example.novelreader.data.remote.crawler.NovelCrawlerService
import com.example.novelreader.data.repository.CommunityRepository
import com.example.novelreader.domain.model.*
import com.example.novelreader.domain.repository.BookRepository
import com.example.novelreader.domain.repository.ChapterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.datastore.preferences.core.Preferences

// ============================================================
// BOOKSHELF VIEW MODEL
// ============================================================

@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val bookRepo: BookRepository
) : ViewModel() {

    val books: StateFlow<List<Book>> = bookRepo.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Map bookId -> ReadingHistory for progress display
    private val _progressMap = MutableStateFlow<Map<String, ReadingHistory>>(emptyMap())
    val progressMap: StateFlow<Map<String, ReadingHistory>> = _progressMap.asStateFlow()

    private val _uiState = MutableStateFlow<BookshelfUiState>(BookshelfUiState.Idle)
    val uiState: StateFlow<BookshelfUiState> = _uiState.asStateFlow()

    init {
        // Collect reading progress for all books
        viewModelScope.launch {
            books.collectLatest { bookList ->
                val map = bookList.associate { book ->
                    val history = bookRepo.getReadingProgress(book.id).firstOrNull()
                    book.id to (history ?: ReadingHistory(bookId = book.id))
                }
                _progressMap.value = map
            }
        }
    }

    fun deleteBook(bookId: String) = viewModelScope.launch {
        bookRepo.deleteBook(bookId)
    }

    fun importTxtFile(rawText: String, title: String, customRegex: String?) =
        viewModelScope.launch {
            _uiState.value = BookshelfUiState.Loading
            bookRepo.importTxtFile(rawText, title, customRegex)
                .onSuccess { _uiState.value = BookshelfUiState.ImportSuccess(it) }
                .onFailure { _uiState.value = BookshelfUiState.Error(it.message ?: "Lỗi nhập file") }
        }
}

sealed class BookshelfUiState {
    object Idle : BookshelfUiState()
    object Loading : BookshelfUiState()
    data class ImportSuccess(val book: Book) : BookshelfUiState()
    data class Error(val message: String) : BookshelfUiState()
}

// ============================================================
// DISCOVER VIEW MODEL
// ============================================================

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val crawler: NovelCrawlerService,
    private val bookRepo: BookRepository
) : ViewModel() {

    private val _novels = MutableStateFlow<List<Book>>(emptyList())
    val novels: StateFlow<List<Book>> = _novels.asStateFlow()

    private val _detailState = MutableStateFlow<DetailUiState>(DetailUiState.Idle)
    val detailState: StateFlow<DetailUiState> = _detailState.asStateFlow()

    private val _uiState = MutableStateFlow<DiscoverUiState>(DiscoverUiState.Idle)
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    // Currently selected crawler config
    var currentConfig: CrawlerConfig = CrawlerConfigs.TRUYEN_FULL

    fun loadNovels(pageUrl: String = "${CrawlerConfigs.TRUYEN_FULL.baseUrl}/danh-sach/truyen-full") {
        viewModelScope.launch {
            _uiState.value = DiscoverUiState.Loading
            crawler.discoverNovels(pageUrl, currentConfig)
                .onSuccess {
                    _novels.value = it
                    _uiState.value = DiscoverUiState.Success
                }
                .onFailure {
                    _uiState.value = DiscoverUiState.Error(it.message ?: "Không thể tải danh sách truyện")
                }
        }
    }

    fun loadNovelDetail(detailUrl: String) {
        viewModelScope.launch {
            _detailState.value = DetailUiState.Loading
            crawler.fetchNovelDetail(detailUrl, currentConfig)
                .onSuccess { (book, chapters) ->
                    _detailState.value = DetailUiState.Success(book, chapters)
                }
                .onFailure {
                    _detailState.value = DetailUiState.Error(it.message ?: "Không thể tải chi tiết truyện")
                }
        }
    }

    fun addToBookshelf(detailUrl: String) = viewModelScope.launch {
        bookRepo.crawlAndSaveNovel(detailUrl, currentConfig)
    }
}

sealed class DiscoverUiState {
    object Idle : DiscoverUiState()
    object Loading : DiscoverUiState()
    object Success : DiscoverUiState()
    data class Error(val message: String) : DiscoverUiState()
}

sealed class DetailUiState {
    object Idle : DetailUiState()
    object Loading : DetailUiState()
    data class Success(val book: Book, val chapters: List<Chapter>) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

// ============================================================
// READER VIEW MODEL
// ============================================================

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val chapterRepo: ChapterRepository,
    private val bookRepo: BookRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _currentChapter = MutableStateFlow<Chapter?>(null)
    val currentChapter: StateFlow<Chapter?> = _currentChapter.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    private val _readerState = MutableStateFlow<ReaderUiState>(ReaderUiState.Idle)
    val readerState: StateFlow<ReaderUiState> = _readerState.asStateFlow()

    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    private val _translationState = MutableStateFlow<TranslationState>(TranslationState.Idle)
    val translationState: StateFlow<TranslationState> = _translationState.asStateFlow()

    // Companion keys for DataStore
    companion object {
        val FONT_SIZE = floatPreferencesKey("font_size")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val BG_COLOR = stringPreferencesKey("bg_color")
        val IS_DARK_MODE = booleanPreferencesKey("dark_mode")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val TRANSLATION_PROMPT = stringPreferencesKey("translation_prompt")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
    }

    init {
        // Load settings from DataStore
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _settings.value = ReaderSettings(
                    fontSize = prefs[FONT_SIZE] ?: 18f,
                    fontFamily = prefs[FONT_FAMILY] ?: "default",
                    backgroundColor = ReaderBackground.valueOf(prefs[BG_COLOR] ?: "PAPER"),
                    isDarkMode = prefs[IS_DARK_MODE] ?: false,
                    geminiApiKey = prefs[GEMINI_API_KEY] ?: "",
                    translationPrompt = prefs[TRANSLATION_PROMPT] ?: DEFAULT_TRANSLATION_PROMPT,
                    lineSpacing = prefs[LINE_SPACING] ?: 1.6f
                )
            }
        }
    }

    fun loadBook(bookId: String) {
        viewModelScope.launch {
            chapterRepo.getChaptersByBook(bookId).collect { _chapters.value = it }
        }
    }

    fun openChapter(chapter: Chapter, crawlerConfig: CrawlerConfig? = null) {
        viewModelScope.launch {
            _readerState.value = ReaderUiState.Loading
            chapterRepo.ensureContentLoaded(chapter, crawlerConfig)
                .onSuccess {
                    _currentChapter.value = it
                    _readerState.value = ReaderUiState.Ready
                }
                .onFailure {
                    _readerState.value = ReaderUiState.Error(it.message ?: "Không thể tải chương")
                }
        }
    }

    fun navigateChapter(delta: Int, crawlerConfig: CrawlerConfig? = null) {
        val current = _currentChapter.value ?: return
        val next = _chapters.value.firstOrNull { it.chapterIndex == current.chapterIndex + delta }
        next?.let { openChapter(it, crawlerConfig) }
    }

    fun translateCurrentChapter() {
        val chapter = _currentChapter.value ?: return
        val apiKey = _settings.value.geminiApiKey
        val prompt = _settings.value.translationPrompt

        if (apiKey.isBlank()) {
            _translationState.value = TranslationState.Error("Bạn chưa nhập Gemini API Key")
            return
        }

        // If already translated, just show it
        if (chapter.translatedContent.isNotBlank()) {
            _translationState.value = TranslationState.Done(chapter.translatedContent)
            return
        }

        viewModelScope.launch {
            _translationState.value = TranslationState.Translating
            chapterRepo.translateChapter(chapter, prompt, apiKey)
                .onSuccess { translation ->
                    _currentChapter.value = chapter.copy(translatedContent = translation)
                    _translationState.value = TranslationState.Done(translation)
                }
                .onFailure {
                    _translationState.value = TranslationState.Error(it.message ?: "Lỗi dịch")
                }
        }
    }

    fun saveProgress(bookId: String, chapterId: String, chapterIndex: Int, scrollPosition: Float) {
        viewModelScope.launch {
            bookRepo.updateReadingProgress(
                ReadingHistory(
                    bookId = bookId,
                    chapterId = chapterId,
                    chapterIndex = chapterIndex,
                    scrollPosition = scrollPosition
                )
            )
        }
    }

    fun updateFontSize(size: Float) = viewModelScope.launch {
        dataStore.edit { it[FONT_SIZE] = size }
    }

    fun updateFontFamily(family: String) = viewModelScope.launch {
        dataStore.edit { it[FONT_FAMILY] = family }
    }

    fun updateBackground(bg: ReaderBackground) = viewModelScope.launch {
        dataStore.edit { it[BG_COLOR] = bg.name }
    }

    fun updateDarkMode(enabled: Boolean) = viewModelScope.launch {
        dataStore.edit { it[IS_DARK_MODE] = enabled }
    }

    fun updateApiKey(key: String) = viewModelScope.launch {
        dataStore.edit { it[GEMINI_API_KEY] = key }
    }

    fun updateTranslationPrompt(prompt: String) = viewModelScope.launch {
        dataStore.edit { it[TRANSLATION_PROMPT] = prompt }
    }
}

sealed class ReaderUiState {
    object Idle : ReaderUiState()
    object Loading : ReaderUiState()
    object Ready : ReaderUiState()
    data class Error(val message: String) : ReaderUiState()
}

sealed class TranslationState {
    object Idle : TranslationState()
    object Translating : TranslationState()
    data class Done(val translation: String) : TranslationState()
    data class Error(val message: String) : TranslationState()
}

// ============================================================
// COMMUNITY VIEW MODEL
// ============================================================

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val communityRepo: CommunityRepository
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> = communityRepo.getChatMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val prompts: StateFlow<List<Prompt>> = communityRepo.getPrompts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sharedNovels: StateFlow<List<SharedNovel>> = communityRepo.getSharedNovels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isLoggedIn: Boolean get() = communityRepo.isLoggedIn

    private val _actionState = MutableStateFlow<CommunityActionState>(CommunityActionState.Idle)
    val actionState: StateFlow<CommunityActionState> = _actionState.asStateFlow()

    fun sendMessage(content: String, replyToId: String? = null, replyToContent: String? = null) =
        viewModelScope.launch {
            communityRepo.sendMessage(content, replyToId, replyToContent)
                .onFailure { _actionState.value = CommunityActionState.Error(it.message ?: "Lỗi gửi tin") }
        }

    fun postPrompt(title: String, content: String) = viewModelScope.launch {
        _actionState.value = CommunityActionState.Loading
        communityRepo.postPrompt(title, content)
            .onSuccess { _actionState.value = CommunityActionState.Success("Đăng prompt thành công!") }
            .onFailure { _actionState.value = CommunityActionState.Error(it.message ?: "Lỗi đăng prompt") }
    }

    fun likePrompt(promptId: String) = viewModelScope.launch {
        communityRepo.likePrompt(promptId)
    }

    fun shareNovel(title: String, fileBytes: ByteArray) = viewModelScope.launch {
        _actionState.value = CommunityActionState.Loading
        communityRepo.shareNovelFile(title, fileBytes)
            .onSuccess { _actionState.value = CommunityActionState.Success("Chia sẻ thành công!") }
            .onFailure { _actionState.value = CommunityActionState.Error(it.message ?: "Lỗi chia sẻ") }
    }

    fun clearActionState() { _actionState.value = CommunityActionState.Idle }
}

sealed class CommunityActionState {
    object Idle : CommunityActionState()
    object Loading : CommunityActionState()
    data class Success(val message: String) : CommunityActionState()
    data class Error(val message: String) : CommunityActionState()
}

// ============================================================
// PROFILE / AUTH VIEW MODEL
// ============================================================

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val communityRepo: CommunityRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val isLoggedIn: Boolean get() = communityRepo.isLoggedIn

    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _settings.value = _settings.value.copy(
                    geminiApiKey = prefs[ReaderViewModel.GEMINI_API_KEY] ?: "",
                    translationPrompt = prefs[ReaderViewModel.TRANSLATION_PROMPT] ?: DEFAULT_TRANSLATION_PROMPT,
                    fontSize = prefs[ReaderViewModel.FONT_SIZE] ?: 18f,
                    fontFamily = prefs[ReaderViewModel.FONT_FAMILY] ?: "default",
                    isDarkMode = prefs[ReaderViewModel.IS_DARK_MODE] ?: false
                )
            }
        }
    }

    fun signUp(name: String, email: String, password: String) = viewModelScope.launch {
        _authState.value = AuthState.Loading
        communityRepo.signUp(name, email, password)
            .onSuccess { _authState.value = AuthState.LoggedIn(it) }
            .onFailure { _authState.value = AuthState.Error(it.message ?: "Đăng ký thất bại") }
    }

    fun signIn(email: String, password: String) = viewModelScope.launch {
        _authState.value = AuthState.Loading
        communityRepo.signIn(email, password)
            .onSuccess { _authState.value = AuthState.LoggedIn(it) }
            .onFailure { _authState.value = AuthState.Error(it.message ?: "Đăng nhập thất bại") }
    }

    fun signOut() = viewModelScope.launch {
        communityRepo.signOut()
        _authState.value = AuthState.LoggedOut
    }

    fun saveApiKey(key: String) = viewModelScope.launch {
        dataStore.edit { it[ReaderViewModel.GEMINI_API_KEY] = key }
    }

    fun saveTranslationPrompt(prompt: String) = viewModelScope.launch {
        dataStore.edit { it[ReaderViewModel.TRANSLATION_PROMPT] = prompt }
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object LoggedOut : AuthState()
    data class LoggedIn(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}
