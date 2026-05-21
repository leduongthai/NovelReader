package com.example.novelreader.presentation.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import com.example.novelreader.data.repository.ReviewRepository
import com.example.novelreader.domain.model.Review
import kotlinx.coroutines.flow.map
import com.example.novelreader.data.remote.crawler.DiscoverFeed
import com.example.novelreader.data.remote.crawler.SourceGroup

// ============================================================
// BOOKSHELF VIEW MODEL
// ============================================================

@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val bookRepo: BookRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    val books: StateFlow<List<Book>> = bookRepo.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val autoOpenLastBook: StateFlow<Boolean> = dataStore.data
        .map { it[ReaderViewModel.AUTO_OPEN_LAST_BOOK] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val progressMap: StateFlow<Map<String, ReadingHistory>> =
        combine(books, bookRepo.getAllReadingProgress()) { bookList, histories ->
            val bookIds = bookList.map { it.id }.toSet()
            histories
                .filter { it.bookId in bookIds }
                .distinctBy { it.bookId }
                .associateBy { it.bookId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _uiState = MutableStateFlow<BookshelfUiState>(BookshelfUiState.Idle)
    val uiState: StateFlow<BookshelfUiState> = _uiState.asStateFlow()

    fun deleteBook(bookId: String) = viewModelScope.launch {
        bookRepo.deleteBook(bookId)
    }

    fun importTxtFile(rawText: String, title: String, customRegex: String?) =
        viewModelScope.launch {
            _uiState.value = BookshelfUiState.Loading
            bookRepo.importTxtFile(rawText, title, customRegex)
                .onSuccess {
                    _uiState.value = BookshelfUiState.ImportSuccess(it)
                    kotlinx.coroutines.delay(100)
                    _uiState.value = BookshelfUiState.Idle
                }
                .onFailure {
                    _uiState.value = BookshelfUiState.Error(it.message ?: "Lỗi nhập file")
                    kotlinx.coroutines.delay(100)
                    _uiState.value = BookshelfUiState.Idle
                }
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

    private val _selectedGroup = MutableStateFlow(SourceGroup.VIETNAMESE)
    val selectedGroup: StateFlow<SourceGroup> = _selectedGroup.asStateFlow()

    private val _selectedFeed = MutableStateFlow(DiscoverFeed.VI_TRUYENFULL_NEW)
    val selectedFeed: StateFlow<DiscoverFeed> = _selectedFeed.asStateFlow()

    private val _detailState = MutableStateFlow<DetailUiState>(DetailUiState.Idle)
    val detailState: StateFlow<DetailUiState> = _detailState.asStateFlow()

    private val _detailActionState = MutableStateFlow<DetailActionState>(DetailActionState.Idle)
    val detailActionState: StateFlow<DetailActionState> = _detailActionState.asStateFlow()

    private val _uiState = MutableStateFlow<DiscoverUiState>(DiscoverUiState.Idle)
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    fun loadNovels(page: Int = 1) {
        viewModelScope.launch {
            _uiState.value = DiscoverUiState.Loading
            crawler.discoverNovels(page = page, feed = _selectedFeed.value)
                .onSuccess {
                    _novels.value = it
                    _uiState.value = DiscoverUiState.Success
                }
                .onFailure {
                    _uiState.value = DiscoverUiState.Error(it.message ?: "Không thể tải danh sách truyện")
                }
        }
    }

    fun selectFeed(feed: DiscoverFeed) {
        if (_selectedFeed.value == feed || !feed.enabled) return
        _selectedGroup.value = feed.group
        _selectedFeed.value = feed
        _searchQuery.value = ""
        loadNovels()
    }

    fun selectGroup(group: SourceGroup) {
        if (_selectedGroup.value == group) return
        val firstFeed = DiscoverFeed.entries.firstOrNull { it.group == group && it.enabled } ?: return
        _selectedGroup.value = group
        _selectedFeed.value = firstFeed
        _searchQuery.value = ""
        loadNovels()
    }

    fun loadNovelDetail(detailUrl: String) {
        viewModelScope.launch {
            _detailState.value = DetailUiState.Loading
            crawler.fetchNovelDetail(detailUrl)
                .onSuccess { (book, chapters) ->
                    _detailState.value = DetailUiState.Success(book, chapters)
                }
                .onFailure {
                    _detailState.value = DetailUiState.Error(it.message ?: "Không thể tải chi tiết truyện")
                }
        }
    }

    fun addToBookshelf(detailUrl: String) = viewModelScope.launch {
        _detailActionState.value = DetailActionState.Loading
        val existing = bookRepo.getBookBySourceUrl(detailUrl)
        if (existing != null) {
            _detailActionState.value = DetailActionState.Added(existing, alreadyInShelf = true)
            return@launch
        }

        bookRepo.crawlAndSaveNovel(detailUrl)
            .onSuccess { _detailActionState.value = DetailActionState.Added(it) }
            .onFailure {
                _detailActionState.value =
                    DetailActionState.Error(it.message ?: "Không thể thêm truyện vào kệ sách")
            }
    }

    fun clearDetailActionState() {
        _detailActionState.value = DetailActionState.Idle
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredNovels: StateFlow<List<Book>> = combine(_novels, _searchQuery) { list, query ->
        if (query.isBlank()) list
        else list.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.author.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: kotlinx.coroutines.Job? = null

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun searchRemote(query: String) {
        if (query.isBlank()) {
            loadNovels()
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = DiscoverUiState.Loading
            crawler.searchNovels(query, feed = _selectedFeed.value)
                .onSuccess {
                    _novels.value = it
                    _uiState.value = DiscoverUiState.Success
                }
                .onFailure {
                    _uiState.value = DiscoverUiState.Error(it.message ?: "Tìm kiếm thất bại")
                }
        }
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

sealed class DetailActionState {
    object Idle : DetailActionState()
    object Loading : DetailActionState()
    data class Added(val book: Book, val alreadyInShelf: Boolean = false) : DetailActionState()
    data class Error(val message: String) : DetailActionState()
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

    private val _scrollRequests = MutableSharedFlow<Float>(extraBufferCapacity = 1)
    val scrollRequests: SharedFlow<Float> = _scrollRequests.asSharedFlow()

    companion object {
        val FONT_SIZE = floatPreferencesKey("font_size")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val BG_COLOR = stringPreferencesKey("bg_color")
        val IS_DARK_MODE = booleanPreferencesKey("dark_mode")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val TRANSLATION_PROMPT = stringPreferencesKey("translation_prompt")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
        val AUTO_OPEN_LAST_BOOK = booleanPreferencesKey("auto_open_last_book")
    }

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _settings.value = ReaderSettings(
                    fontSize = prefs[FONT_SIZE] ?: 18f,
                    fontFamily = prefs[FONT_FAMILY] ?: "default",
                    backgroundColor = ReaderBackground.valueOf(prefs[BG_COLOR] ?: "PAPER"),
                    isDarkMode = prefs[IS_DARK_MODE] ?: false,
                    autoOpenLastBook = prefs[AUTO_OPEN_LAST_BOOK] ?: false,
                    geminiApiKey = prefs[GEMINI_API_KEY] ?: "",
                    translationPrompt = prefs[TRANSLATION_PROMPT] ?: DEFAULT_TRANSLATION_PROMPT,
                    lineSpacing = prefs[LINE_SPACING] ?: 1.6f
                )
            }
        }
    }

    fun loadBook(bookId: String) {
        viewModelScope.launch {
            chapterRepo.getChaptersByBook(bookId).collect { loadedChapters ->
                _chapters.value = loadedChapters
                _currentChapter.value?.let { current ->
                    loadedChapters.firstOrNull { it.id == current.id }?.let { refreshed ->
                        _currentChapter.value = refreshed
                    }
                }
            }
        }
    }

    fun openChapter(chapter: Chapter, targetScrollFraction: Float = 0f) {
        viewModelScope.launch {
            _readerState.value = ReaderUiState.Loading
            _translationState.value = TranslationState.Idle
            chapterRepo.ensureContentLoaded(chapter)
                .onSuccess {
                    _currentChapter.value = it
                    _readerState.value = ReaderUiState.Ready
                    _scrollRequests.emit(targetScrollFraction.coerceIn(0f, 1f))
                }
                .onFailure {
                    _readerState.value = ReaderUiState.Error(it.message ?: "Không thể tải chương")
                }
        }
    }

    fun navigateChapter(delta: Int, currentScrollFraction: Float = 0f) {
        val current = _currentChapter.value ?: return
        val next = _chapters.value.firstOrNull { it.chapterIndex == current.chapterIndex + delta }
        next?.let { openChapter(it, currentScrollFraction) }
    }

    fun translateCurrentChapter(force: Boolean = false) {
        val chapter = _currentChapter.value ?: return
        val apiKey = _settings.value.geminiApiKey
        val prompt = _settings.value.translationPrompt

        if (apiKey.isBlank()) {
            _translationState.value = TranslationState.Error("Bạn chưa nhập Gemini API Key")
            return
        }

        if (!force && chapter.translatedContent.isNotBlank() && chapter.translatedTitle.isNotBlank()) {
            _translationState.value = TranslationState.Done(chapter.id)
            return
        }

        viewModelScope.launch {
            _translationState.value = TranslationState.Translating
            if (!force && chapter.translatedContent.isNotBlank()) {
                chapterRepo.translateChapterTitle(chapter, prompt, apiKey)
                    .onSuccess { translatedTitle ->
                        val translatedChapter = chapter.copy(translatedTitle = translatedTitle)
                        updateTranslatedChapter(chapter.id, translatedChapter)
                        _translationState.value = TranslationState.Done(chapter.id)
                    }
                    .onFailure {
                        _translationState.value = TranslationState.Error(it.message ?: "Lỗi dịch")
                    }
                return@launch
            }

            chapterRepo.translateChapter(chapter, prompt, apiKey)
                .onSuccess { translation ->
                    val translatedChapter = chapter.copy(
                        translatedTitle = translation.title,
                        translatedContent = translation.content
                    )
                    updateTranslatedChapter(chapter.id, translatedChapter)
                    _translationState.value = TranslationState.Done(chapter.id)
                }
                .onFailure {
                    _translationState.value = TranslationState.Error(it.message ?: "Lỗi dịch")
                }
        }
    }

    private fun updateTranslatedChapter(chapterId: String, translatedChapter: Chapter) {
        _chapters.value = _chapters.value.map {
            if (it.id == chapterId) translatedChapter else it
        }
        if (_currentChapter.value?.id == chapterId) {
            _currentChapter.value = translatedChapter
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

    fun updateFontSize(size: Float) = viewModelScope.launch { dataStore.edit { it[FONT_SIZE] = size } }
    fun updateFontFamily(family: String) = viewModelScope.launch { dataStore.edit { it[FONT_FAMILY] = family } }
    fun updateBackground(bg: ReaderBackground) = viewModelScope.launch { dataStore.edit { it[BG_COLOR] = bg.name } }
    fun updateDarkMode(enabled: Boolean) = viewModelScope.launch { dataStore.edit { it[IS_DARK_MODE] = enabled } }
    fun updateLineSpacing(value: Float) = viewModelScope.launch { dataStore.edit { it[LINE_SPACING] = value } }
    fun updateAutoOpenLastBook(enabled: Boolean) = viewModelScope.launch { dataStore.edit { it[AUTO_OPEN_LAST_BOOK] = enabled } }
    fun updateApiKey(key: String) = viewModelScope.launch { dataStore.edit { it[GEMINI_API_KEY] = key.trim() } }
    fun updateTranslationPrompt(prompt: String) = viewModelScope.launch { dataStore.edit { it[TRANSLATION_PROMPT] = prompt } }

    fun toggleBookmark() {
        val chapter = _currentChapter.value ?: return
        viewModelScope.launch {
            val newState = !chapter.isBookmarked
            chapterRepo.toggleBookmark(chapter.id, newState)
            _currentChapter.value = chapter.copy(isBookmarked = newState)
        }
    }

    fun getShareLink(bookId: String): String = "https://novelreader.app/story/$bookId"
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
    data class Done(val chapterId: String) : TranslationState()
    data class Error(val message: String) : TranslationState()
}

// ============================================================
// COMMUNITY VIEW MODEL
// ============================================================

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val communityRepo: CommunityRepository,
    private val bookRepo: BookRepository
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> = communityRepo.getChatMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val currentUserId: String? get() = communityRepo.currentUserId

    val prompts: StateFlow<List<Prompt>> = communityRepo.getPrompts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sharedNovels: StateFlow<List<SharedNovel>> = communityRepo.getSharedNovels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val promptComments: StateFlow<Map<String, List<CommunityComment>>> =
        communityRepo.getComments("prompt")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val sharedNovelComments: StateFlow<Map<String, List<CommunityComment>>> =
        communityRepo.getComments("shared_novel")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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
            .onFailure { _actionState.value = CommunityActionState.Error(it.message ?: "Lỗi đánh giá prompt") }
    }

    fun postPromptComment(promptId: String, content: String) = viewModelScope.launch {
        communityRepo.postComment("prompt", promptId, content)
            .onSuccess { _actionState.value = CommunityActionState.Success("Đã gửi bình luận") }
            .onFailure { _actionState.value = CommunityActionState.Error(it.message ?: "Lỗi gửi bình luận") }
    }

    fun postSharedNovelComment(novelId: String, content: String) = viewModelScope.launch {
        communityRepo.postComment("shared_novel", novelId, content)
            .onSuccess { _actionState.value = CommunityActionState.Success("Đã gửi bình luận") }
            .onFailure { _actionState.value = CommunityActionState.Error(it.message ?: "Lỗi gửi bình luận") }
    }

    // --- Xóa nội dung (chủ bài hoặc Mod/Admin) ---

    fun deleteChatMessage(messageId: String) = viewModelScope.launch {
        communityRepo.deleteChatMessage(messageId)
            .onSuccess { _actionState.value = CommunityActionState.Success("Đã xóa tin nhắn") }
            .onFailure { _actionState.value = CommunityActionState.Error(it.message ?: "Lỗi xóa tin nhắn") }
    }

    fun deletePrompt(promptId: String) = viewModelScope.launch {
        communityRepo.deletePrompt(promptId)
            .onSuccess { _actionState.value = CommunityActionState.Success("Đã xóa bài viết") }
            .onFailure { _actionState.value = CommunityActionState.Error(it.message ?: "Lỗi xóa bài viết") }
    }

    fun deleteSharedNovel(novelId: String) = viewModelScope.launch {
        communityRepo.deleteSharedNovel(novelId)
            .onSuccess { _actionState.value = CommunityActionState.Success("Đã xóa truyện chia sẻ") }
            .onFailure { _actionState.value = CommunityActionState.Error(it.message ?: "Lỗi xóa truyện") }
    }

    fun shareNovel(title: String, fileBytes: ByteArray) = viewModelScope.launch {
        _actionState.value = CommunityActionState.Loading
        communityRepo.shareNovelFile(title, fileBytes)
            .onSuccess { _actionState.value = CommunityActionState.Success("Chia sẻ thành công!") }
            .onFailure { _actionState.value = CommunityActionState.Error(it.message ?: "Lỗi chia sẻ") }
    }

    fun downloadAndImport(novel: SharedNovel) = viewModelScope.launch {
        _actionState.value = CommunityActionState.Loading
        // Lấy nội dung từ RTDB (không dùng Storage URL nữa)
        communityRepo.fetchSharedNovelContent(novel.fileUrl)
            .mapCatching { text ->
                bookRepo.importTxtFile(text, novel.title, null).getOrThrow()
            }
            .onSuccess {
                communityRepo.incrementSharedNovelDownload(novel.id)
                _actionState.value = CommunityActionState.Success("Đã thêm \"${novel.title}\" vào kệ sách!")
            }
            .onFailure { _actionState.value = CommunityActionState.Error(it.message ?: "Lỗi tải truyện") }
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

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    private val _profileAction = MutableStateFlow<ProfileActionState>(ProfileActionState.Idle)
    val profileAction: StateFlow<ProfileActionState> = _profileAction.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _settings.value = _settings.value.copy(
                    geminiApiKey      = prefs[ReaderViewModel.GEMINI_API_KEY] ?: "",
                    translationPrompt = prefs[ReaderViewModel.TRANSLATION_PROMPT] ?: DEFAULT_TRANSLATION_PROMPT,
                    fontSize          = prefs[ReaderViewModel.FONT_SIZE] ?: 18f,
                    fontFamily        = prefs[ReaderViewModel.FONT_FAMILY] ?: "default",
                    lineSpacing       = prefs[ReaderViewModel.LINE_SPACING] ?: 1.6f,
                    backgroundColor   = ReaderBackground.valueOf(prefs[ReaderViewModel.BG_COLOR] ?: ReaderBackground.PAPER.name),
                    isDarkMode        = prefs[ReaderViewModel.IS_DARK_MODE] ?: false,
                    autoOpenLastBook  = prefs[ReaderViewModel.AUTO_OPEN_LAST_BOOK] ?: false
                )
            }
        }
        // Load user data nếu đã đăng nhập
        if (isLoggedIn) refreshCurrentUser()
    }

    fun refreshCurrentUser() = viewModelScope.launch {
        val user = communityRepo.fetchCurrentUser()
        _currentUser.value = user
        if (user != null) _authState.value = AuthState.LoggedIn(user)
    }

    fun signUp(name: String, email: String, password: String) = viewModelScope.launch {
        _authState.value = AuthState.Loading
        communityRepo.signUp(name, email, password)
            .onSuccess { user ->
                _currentUser.value = user
                _authState.value = AuthState.LoggedIn(user)
            }
            .onFailure { _authState.value = AuthState.Error(it.message ?: "Đăng ký thất bại") }
    }

    fun signIn(email: String, password: String) = viewModelScope.launch {
        _authState.value = AuthState.Loading
        communityRepo.signIn(email, password)
            .onSuccess { user ->
                _currentUser.value = user
                _authState.value = AuthState.LoggedIn(user)
            }
            .onFailure { _authState.value = AuthState.Error(it.message ?: "Đăng nhập thất bại") }
    }

    fun signOut() = viewModelScope.launch {
        communityRepo.signOut()
        _currentUser.value = null
        _authState.value = AuthState.LoggedOut
    }

    fun resetPassword(email: String) = viewModelScope.launch {
        if (email.isBlank()) { _authState.value = AuthState.Error("Vui lòng nhập email"); return@launch }
        _authState.value = AuthState.Loading
        try {
            communityRepo.auth.sendPasswordResetEmail(email).await()
            _authState.value = AuthState.PasswordResetSent
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Gửi email thất bại")
        }
    }






    fun updateProfile(name: String, avatarUrl: String = "") = viewModelScope.launch {
        if (name.isBlank()) { _profileAction.value = ProfileActionState.Error("Tên không được trống"); return@launch }
        _profileAction.value = ProfileActionState.Loading
        communityRepo.updateProfile(name, avatarUrl)
            .onSuccess {
                _currentUser.value = _currentUser.value?.copy(
                    name = name.trim(),
                    avatarUrl = avatarUrl.ifBlank { _currentUser.value?.avatarUrl.orEmpty() }
                )
                _profileAction.value = ProfileActionState.Success("Đã cập nhật thông tin")
                kotlinx.coroutines.delay(100)
                _profileAction.value = ProfileActionState.Idle
            }
            .onFailure {
                _profileAction.value = ProfileActionState.Error(it.message ?: "Cập nhật thất bại")
                kotlinx.coroutines.delay(100)
                _profileAction.value = ProfileActionState.Idle
            }
    }

    fun updateProfileAvatar(name: String, fileBytes: ByteArray, contentType: String?) = viewModelScope.launch {
        if (name.isBlank()) {
            _profileAction.value = ProfileActionState.Error("Tên không được trống")
            return@launch
        }
        _profileAction.value = ProfileActionState.Loading
        communityRepo.uploadAvatar(fileBytes, contentType)
            .fold(
                onSuccess = { remoteUrl ->
                    communityRepo.updateProfile(name, remoteUrl)
                        .onSuccess {
                            _currentUser.value = _currentUser.value?.copy(
                                name = name.trim(),
                                avatarUrl = remoteUrl
                            )
                            _profileAction.value = ProfileActionState.Success("Đã cập nhật ảnh đại diện")
                            kotlinx.coroutines.delay(100)
                            _profileAction.value = ProfileActionState.Idle
                        }
                        .onFailure {
                            _profileAction.value = ProfileActionState.Error(it.message ?: "Cập nhật ảnh thất bại")
                            kotlinx.coroutines.delay(100)
                            _profileAction.value = ProfileActionState.Idle
                        }
                },
                onFailure = {
                    _profileAction.value = ProfileActionState.Error(it.message ?: "Tải ảnh thất bại")
                    kotlinx.coroutines.delay(100)
                    _profileAction.value = ProfileActionState.Idle
                }
            )
    }

    fun saveApiKey(key: String) = viewModelScope.launch { dataStore.edit { it[ReaderViewModel.GEMINI_API_KEY] = key.trim() } }
    fun saveTranslationPrompt(prompt: String) = viewModelScope.launch { dataStore.edit { it[ReaderViewModel.TRANSLATION_PROMPT] = prompt } }
    fun saveFontSize(size: Float) = viewModelScope.launch {
        dataStore.edit { it[ReaderViewModel.FONT_SIZE] = size.coerceIn(12f, 36f) }
    }
    fun saveFontFamily(family: String) = viewModelScope.launch { dataStore.edit { it[ReaderViewModel.FONT_FAMILY] = family } }
    fun saveLineSpacing(value: Float) = viewModelScope.launch {
        dataStore.edit { it[ReaderViewModel.LINE_SPACING] = value.coerceIn(1.2f, 2.4f) }
    }
    fun saveReaderBackground(bg: ReaderBackground) = viewModelScope.launch { dataStore.edit { it[ReaderViewModel.BG_COLOR] = bg.name } }
    fun saveDarkMode(enabled: Boolean) = viewModelScope.launch { dataStore.edit { it[ReaderViewModel.IS_DARK_MODE] = enabled } }
    fun saveAutoOpenLastBook(enabled: Boolean) = viewModelScope.launch { dataStore.edit { it[ReaderViewModel.AUTO_OPEN_LAST_BOOK] = enabled } }
}

sealed class ProfileActionState {
    object Idle : ProfileActionState()
    object Loading : ProfileActionState()
    data class Success(val message: String) : ProfileActionState()
    data class Error(val message: String) : ProfileActionState()
}

// ============================================================
// ADMIN VIEW MODEL — Quản lý người dùng (Mod / Admin)
// ============================================================

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val communityRepo: CommunityRepository
) : ViewModel() {

    val users: StateFlow<List<UserSummary>> = communityRepo.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _actionState = MutableStateFlow<AdminActionState>(AdminActionState.Idle)
    val actionState: StateFlow<AdminActionState> = _actionState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredUsers: StateFlow<List<UserSummary>> = combine(users, _searchQuery) { list, q ->
        if (q.isBlank()) list
        else list.filter {
            it.name.contains(q, ignoreCase = true) ||
            it.email.contains(q, ignoreCase = true) ||
            it.role.contains(q, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChange(q: String) { _searchQuery.value = q }

    /** Ban tạm thời hoặc vĩnh viễn (durationHours = 0 → vĩnh viễn) */
    fun banUser(targetUid: String, reason: String, durationHours: Long = 0L) = viewModelScope.launch {
        if (reason.isBlank()) { _actionState.value = AdminActionState.Error("Vui lòng nhập lý do"); return@launch }
        _actionState.value = AdminActionState.Loading
        communityRepo.banUser(targetUid, reason, durationHours)
            .onSuccess {
                val label = if (durationHours == 0L) "vĩnh viễn" else "${durationHours}h"
                _actionState.value = AdminActionState.Success("Đã khóa tài khoản ($label)")
            }
            .onFailure { _actionState.value = AdminActionState.Error(it.message ?: "Khóa thất bại") }
    }

    fun unbanUser(targetUid: String) = viewModelScope.launch {
        _actionState.value = AdminActionState.Loading
        communityRepo.unbanUser(targetUid)
            .onSuccess { _actionState.value = AdminActionState.Success("Đã mở khóa tài khoản") }
            .onFailure { _actionState.value = AdminActionState.Error(it.message ?: "Mở khóa thất bại") }
    }

    fun promoteToMod(targetUid: String) = viewModelScope.launch {
        _actionState.value = AdminActionState.Loading
        communityRepo.promoteToMod(targetUid)
            .onSuccess { _actionState.value = AdminActionState.Success("Đã thăng cấp lên Mod") }
            .onFailure { _actionState.value = AdminActionState.Error(it.message ?: "Thăng cấp thất bại") }
    }

    fun demoteToUser(targetUid: String) = viewModelScope.launch {
        _actionState.value = AdminActionState.Loading
        communityRepo.demoteToUser(targetUid)
            .onSuccess { _actionState.value = AdminActionState.Success("Đã hạ về Người dùng") }
            .onFailure { _actionState.value = AdminActionState.Error(it.message ?: "Hạ cấp thất bại") }
    }

    fun clearActionState() { _actionState.value = AdminActionState.Idle }
}

sealed class AdminActionState {
    object Idle : AdminActionState()
    object Loading : AdminActionState()
    data class Success(val message: String) : AdminActionState()
    data class Error(val message: String) : AdminActionState()
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object LoggedOut : AuthState()
    object PasswordResetSent : AuthState()
    data class LoggedIn(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

// ============================================================
// BOOK DETAIL VIEW MODEL
// ============================================================

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val bookRepo: BookRepository,
    private val chapterRepo: ChapterRepository
) : ViewModel() {

    private val _book = MutableStateFlow<Book?>(null)
    val book: StateFlow<Book?> = _book.asStateFlow()

    val chapters: StateFlow<List<Chapter>> get() = _chapters
    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    fun loadBook(bookId: String) {
        viewModelScope.launch {
            _book.value = bookRepo.getBookById(bookId)
            chapterRepo.getChaptersByBook(bookId).collect { _chapters.value = it }
        }
    }

    fun updateBookInfo(title: String, author: String) {
        val bookId = _book.value?.id ?: return
        if (title.isBlank()) { _saveState.value = SaveState.Error("Tên truyện không được để trống"); return }
        viewModelScope.launch {
            bookRepo.updateBookInfo(bookId, title.trim(), author.trim())
            _book.value = _book.value?.copy(title = title.trim(), author = author.trim())
            _saveState.value = SaveState.Saved
            kotlinx.coroutines.delay(100)
            _saveState.value = SaveState.Idle
        }
    }

    fun updateBookCover(coverUrl: String) {
        val bookId = _book.value?.id ?: return
        if (coverUrl.isBlank()) return
        viewModelScope.launch {
            bookRepo.updateBookCover(bookId, coverUrl)
            _book.value = _book.value?.copy(coverUrl = coverUrl)
            _saveState.value = SaveState.Saved
            kotlinx.coroutines.delay(100)
            _saveState.value = SaveState.Idle
        }
    }
}

sealed class SaveState {
    object Idle : SaveState()
    object Saved : SaveState()
    data class Error(val message: String) : SaveState()
}

// ============================================================
// REVIEW VIEW MODEL
// ============================================================

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val reviewRepo: ReviewRepository,
    private val communityRepo: CommunityRepository
) : ViewModel() {

    private val _reviews = MutableStateFlow<List<Review>>(emptyList())
    val reviews: StateFlow<List<Review>> = _reviews.asStateFlow()

    private val _myReview = MutableStateFlow<Review?>(null)
    val myReview: StateFlow<Review?> = _myReview.asStateFlow()

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Idle)
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    val isLoggedIn: Boolean get() = communityRepo.isLoggedIn

    val averageRating: StateFlow<Float> = _reviews.map { list ->
        if (list.isEmpty()) 0f else list.sumOf { it.rating }.toFloat() / list.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    private var currentBookKey = ""
    private var currentBookId = ""
    private var currentSourceUrl = ""

    fun loadReviews(bookKey: String, bookId: String = "", sourceUrl: String = "") {
        currentBookKey = bookKey
        currentBookId = bookId
        currentSourceUrl = sourceUrl
        viewModelScope.launch { reviewRepo.getReviews(bookKey).collect { _reviews.value = it } }
        viewModelScope.launch { if (isLoggedIn) _myReview.value = reviewRepo.getMyReview(bookKey) }
    }

    fun submitReview(rating: Int, comment: String) {
        if (rating == 0) { _uiState.value = ReviewUiState.Error("Vui lòng chọn số sao"); return }
        viewModelScope.launch {
            _uiState.value = ReviewUiState.Loading
            reviewRepo.submitReview(currentBookKey, currentBookId, currentSourceUrl, rating, comment)
                .onSuccess {
                    _uiState.value = ReviewUiState.Success("Đánh giá đã được gửi!")
                    _myReview.value = reviewRepo.getMyReview(currentBookKey)
                }
                .onFailure { _uiState.value = ReviewUiState.Error(it.message ?: "Lỗi gửi đánh giá") }
        }
    }

    fun clearUiState() { _uiState.value = ReviewUiState.Idle }
}

sealed class ReviewUiState {
    object Idle : ReviewUiState()
    object Loading : ReviewUiState()
    data class Success(val message: String) : ReviewUiState()
    data class Error(val message: String) : ReviewUiState()
}
