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
import com.example.novelreader.data.repository.GroupRepository
import com.example.novelreader.domain.model.ChatGroup
import com.example.novelreader.data.remote.crawler.DiscoverFeed

// ============================================================
// BOOKSHELF VIEW MODEL
// ============================================================

@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val bookRepo: BookRepository
) : ViewModel() {

    val books: StateFlow<List<Book>> = bookRepo.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _progressMap = MutableStateFlow<Map<String, ReadingHistory>>(emptyMap())
    val progressMap: StateFlow<Map<String, ReadingHistory>> = _progressMap.asStateFlow()

    private val _uiState = MutableStateFlow<BookshelfUiState>(BookshelfUiState.Idle)
    val uiState: StateFlow<BookshelfUiState> = _uiState.asStateFlow()

    init {
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

    private val _selectedFeed = MutableStateFlow(DiscoverFeed.QIDIAN_WEEK)
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
        if (_selectedFeed.value == feed) return
        _selectedFeed.value = feed
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
            crawler.searchNovels(query)
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

    fun openChapter(chapter: Chapter) {
        viewModelScope.launch {
            _readerState.value = ReaderUiState.Loading
            chapterRepo.ensureContentLoaded(chapter)
                .onSuccess {
                    _currentChapter.value = it
                    _readerState.value = ReaderUiState.Ready
                }
                .onFailure {
                    _readerState.value = ReaderUiState.Error(it.message ?: "Không thể tải chương")
                }
        }
    }

    fun navigateChapter(delta: Int) {
        val current = _currentChapter.value ?: return
        val next = _chapters.value.firstOrNull { it.chapterIndex == current.chapterIndex + delta }
        next?.let { openChapter(it) }
    }

    fun translateCurrentChapter() {
        val chapter = _currentChapter.value ?: return
        val apiKey = _settings.value.geminiApiKey
        val prompt = _settings.value.translationPrompt

        if (apiKey.isBlank()) {
            _translationState.value = TranslationState.Error("Bạn chưa nhập Gemini API Key")
            return
        }

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

    fun updateFontSize(size: Float) = viewModelScope.launch { dataStore.edit { it[FONT_SIZE] = size } }
    fun updateFontFamily(family: String) = viewModelScope.launch { dataStore.edit { it[FONT_FAMILY] = family } }
    fun updateBackground(bg: ReaderBackground) = viewModelScope.launch { dataStore.edit { it[BG_COLOR] = bg.name } }
    fun updateDarkMode(enabled: Boolean) = viewModelScope.launch { dataStore.edit { it[IS_DARK_MODE] = enabled } }
    fun updateApiKey(key: String) = viewModelScope.launch { dataStore.edit { it[GEMINI_API_KEY] = key } }
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
    data class Done(val translation: String) : TranslationState()
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
                    isDarkMode        = prefs[ReaderViewModel.IS_DARK_MODE] ?: false
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
                _currentUser.value = _currentUser.value?.copy(name = name.trim())
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

    fun saveApiKey(key: String) = viewModelScope.launch { dataStore.edit { it[ReaderViewModel.GEMINI_API_KEY] = key } }
    fun saveTranslationPrompt(prompt: String) = viewModelScope.launch { dataStore.edit { it[ReaderViewModel.TRANSLATION_PROMPT] = prompt } }
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

// ============================================================
// GROUP VIEW MODEL
// ============================================================

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val groupRepo: GroupRepository
) : ViewModel() {

    private val _groups = MutableStateFlow<List<ChatGroup>>(emptyList())
    val groups: StateFlow<List<ChatGroup>> = _groups.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _currentGroup = MutableStateFlow<ChatGroup?>(null)
    val currentGroup: StateFlow<ChatGroup?> = _currentGroup.asStateFlow()

    private val _uiState = MutableStateFlow<GroupUiState>(GroupUiState.Idle)
    val uiState: StateFlow<GroupUiState> = _uiState.asStateFlow()

    val currentUserId: String? get() = groupRepo.currentUserId
    val isLoggedIn: Boolean get() = groupRepo.isLoggedIn

    init {
        viewModelScope.launch { groupRepo.getGroups().collect { _groups.value = it } }
    }

    fun openGroup(group: ChatGroup) {
        _currentGroup.value = group
        viewModelScope.launch { groupRepo.getMessages(group.id).collect { _messages.value = it } }
    }

    fun createGroup(name: String, description: String) = viewModelScope.launch {
        if (name.isBlank()) { _uiState.value = GroupUiState.Error("Tên nhóm không được trống"); return@launch }
        _uiState.value = GroupUiState.Loading
        groupRepo.createGroup(name, description)
            .onSuccess { _uiState.value = GroupUiState.Success("Tạo nhóm thành công!") }
            .onFailure { _uiState.value = GroupUiState.Error(it.message ?: "Lỗi tạo nhóm") }
    }

    fun joinGroup(groupId: String) = viewModelScope.launch {
        groupRepo.joinGroup(groupId)
            .onSuccess { _uiState.value = GroupUiState.Success("Đã tham gia nhóm!") }
            .onFailure { _uiState.value = GroupUiState.Error(it.message ?: "Lỗi tham gia nhóm") }
    }

    fun leaveGroup(groupId: String) = viewModelScope.launch {
        groupRepo.leaveGroup(groupId)
            .onSuccess { _uiState.value = GroupUiState.Success("Đã rời nhóm") }
            .onFailure { _uiState.value = GroupUiState.Error(it.message ?: "Lỗi rời nhóm") }
    }

    fun sendMessage(content: String, replyToId: String? = null, replyToContent: String? = null) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepo.sendMessage(groupId, content, replyToId, replyToContent)
                .onFailure { _uiState.value = GroupUiState.Error(it.message ?: "Lỗi gửi tin") }
        }
    }

    fun deleteMessage(messageId: String) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepo.deleteMessage(groupId, messageId)
                .onFailure { _uiState.value = GroupUiState.Error(it.message ?: "Lỗi xóa tin") }
        }
    }

    fun kickMember(targetUid: String) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepo.kickMember(groupId, targetUid)
                .onSuccess { _uiState.value = GroupUiState.Success("Đã kick thành viên") }
                .onFailure { _uiState.value = GroupUiState.Error(it.message ?: "Lỗi kick thành viên") }
        }
    }

    fun clearUiState() { _uiState.value = GroupUiState.Idle }
}

sealed class GroupUiState {
    object Idle : GroupUiState()
    object Loading : GroupUiState()
    data class Success(val message: String) : GroupUiState()
    data class Error(val message: String) : GroupUiState()
}
