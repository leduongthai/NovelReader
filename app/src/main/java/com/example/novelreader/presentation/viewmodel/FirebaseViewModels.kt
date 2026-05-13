package com.example.novelreader.presentation.viewmodel

// ============================================================
// FIREBASE VIEW MODELS
//
// These four ViewModels replace the "god" CommunityViewModel
// and ProfileViewModel with focused, testable units.
//
// • UserViewModel    — auth, profile, admin panel
// • ChatViewModel    — global chat room
// • PromptViewModel  — prompt sharing + likes
// • SharedBookViewModel — upload + browse shared novels
// ============================================================

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.novelreader.data.remote.upload.ExternalFileUploadService
import com.example.novelreader.domain.model.*
import com.example.novelreader.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit // Thêm dòng này
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────
// USER VIEW MODEL
// Handles: sign-up, sign-in, profile, admin panel
// ─────────────────────────────────────────────────────────────

@HiltViewModel
class UserViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    // ── Auth state ────────────────────────────────────────────

    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    /** Live profile of the current user; null when signed out. */
    val currentUser: StateFlow<User?> = userRepo.observeCurrentUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isLoggedIn: Boolean get() = userRepo.isLoggedIn

    // ── Profile action state ──────────────────────────────────

    private val _profileAction = MutableStateFlow<SimpleUiState>(SimpleUiState.Idle)
    val profileAction: StateFlow<SimpleUiState> = _profileAction.asStateFlow()

    // ── Admin panel ───────────────────────────────────────────

    val allUsers: StateFlow<List<UserSummary>> = userRepo.observeAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _adminAction = MutableStateFlow<SimpleUiState>(SimpleUiState.Idle)
    val adminAction: StateFlow<SimpleUiState> = _adminAction.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredUsers: StateFlow<List<UserSummary>> = combine(allUsers, _searchQuery) { list, q ->
        if (q.isBlank()) list
        else list.filter {
            it.name.contains(q, ignoreCase = true) ||
            it.email.contains(q, ignoreCase = true) ||
            it.role.contains(q, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchQueryChange(q: String) { _searchQuery.value = q }

    // ── Auth actions ──────────────────────────────────────────

    fun signUp(name: String, email: String, password: String) = viewModelScope.launch {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _authState.value = AuthUiState.Error("Vui lòng điền đầy đủ thông tin")
            return@launch
        }
        _authState.value = AuthUiState.Loading
        userRepo.signUp(name, email, password)
            .onSuccess { _authState.value = AuthUiState.LoggedIn(it) }
            .onFailure { _authState.value = AuthUiState.Error(it.message ?: "Đăng ký thất bại") }
    }

    fun signIn(email: String, password: String) = viewModelScope.launch {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthUiState.Error("Vui lòng nhập email và mật khẩu")
            return@launch
        }
        _authState.value = AuthUiState.Loading
        userRepo.signIn(email, password)
            .onSuccess { _authState.value = AuthUiState.LoggedIn(it) }
            .onFailure { _authState.value = AuthUiState.Error(it.message ?: "Đăng nhập thất bại") }
    }

    fun signOut() = viewModelScope.launch {
        userRepo.signOut()
        _authState.value = AuthUiState.LoggedOut
    }

    fun sendPasswordReset(email: String) = viewModelScope.launch {
        if (email.isBlank()) { _authState.value = AuthUiState.Error("Vui lòng nhập email"); return@launch }
        _authState.value = AuthUiState.Loading
        userRepo.sendPasswordReset(email)
            .onSuccess { _authState.value = AuthUiState.PasswordResetSent }
            .onFailure { _authState.value = AuthUiState.Error(it.message ?: "Gửi email thất bại") }
    }

    fun clearAuthError() { if (_authState.value is AuthUiState.Error) _authState.value = AuthUiState.Idle }

    // ── Profile ───────────────────────────────────────────────

    fun updateProfile(name: String, avatarUrl: String = "") = viewModelScope.launch {
        if (name.isBlank()) { _profileAction.value = SimpleUiState.Error("Tên không được trống"); return@launch }
        _profileAction.value = SimpleUiState.Loading
        userRepo.updateProfile(name, avatarUrl)
            .onSuccess { _profileAction.value = SimpleUiState.Success("Đã cập nhật hồ sơ") }
            .onFailure { _profileAction.value = SimpleUiState.Error(it.message ?: "Cập nhật thất bại") }
    }

    fun clearProfileAction() { _profileAction.value = SimpleUiState.Idle }

    // ── Admin / Mod actions ───────────────────────────────────

    fun banUser(targetUid: String, reason: String, durationHours: Long = 0L) = viewModelScope.launch {
        if (reason.isBlank()) { _adminAction.value = SimpleUiState.Error("Vui lòng nhập lý do"); return@launch }
        _adminAction.value = SimpleUiState.Loading
        userRepo.banUser(targetUid, reason, durationHours)
            .onSuccess {
                val label = if (durationHours == 0L) "vĩnh viễn" else "${durationHours}h"
                _adminAction.value = SimpleUiState.Success("Đã khóa tài khoản ($label)")
            }
            .onFailure { _adminAction.value = SimpleUiState.Error(it.message ?: "Khóa thất bại") }
    }

    fun unbanUser(targetUid: String) = viewModelScope.launch {
        _adminAction.value = SimpleUiState.Loading
        userRepo.unbanUser(targetUid)
            .onSuccess { _adminAction.value = SimpleUiState.Success("Đã mở khóa tài khoản") }
            .onFailure { _adminAction.value = SimpleUiState.Error(it.message ?: "Mở khóa thất bại") }
    }

    fun promoteToMod(targetUid: String) = viewModelScope.launch {
        _adminAction.value = SimpleUiState.Loading
        userRepo.updateRole(targetUid, UserRole.MOD)
            .onSuccess { _adminAction.value = SimpleUiState.Success("Đã thăng cấp lên Mod") }
            .onFailure { _adminAction.value = SimpleUiState.Error(it.message ?: "Thăng cấp thất bại") }
    }

    fun demoteToUser(targetUid: String) = viewModelScope.launch {
        _adminAction.value = SimpleUiState.Loading
        userRepo.updateRole(targetUid, UserRole.USER)
            .onSuccess { _adminAction.value = SimpleUiState.Success("Đã hạ về Người dùng") }
            .onFailure { _adminAction.value = SimpleUiState.Error(it.message ?: "Hạ cấp thất bại") }
    }

    fun clearAdminAction() { _adminAction.value = SimpleUiState.Idle }

    // ── DataStore helpers (API key + settings) ─────────────────

    fun saveApiKey(key: String) = viewModelScope.launch {
        dataStore.edit { it[ReaderViewModel.GEMINI_API_KEY] = key }
    }

    fun saveTranslationPrompt(prompt: String) = viewModelScope.launch {
        dataStore.edit { it[ReaderViewModel.TRANSLATION_PROMPT] = prompt }
    }
}

// ─────────────────────────────────────────────────────────────
// CHAT VIEW MODEL
// Global one-room chat with real-time updates
// ─────────────────────────────────────────────────────────────

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepo: ChatRepository,
    private val userRepo: UserRepository
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> = chatRepo.observeMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentUserId: String? get() = userRepo.currentUserId
    val isLoggedIn: Boolean get() = userRepo.isLoggedIn

    private val _uiState = MutableStateFlow<SimpleUiState>(SimpleUiState.Idle)
    val uiState: StateFlow<SimpleUiState> = _uiState.asStateFlow()

    fun sendMessage(
        content: String,
        replyToId: String? = null,
        replyToContent: String? = null
    ) = viewModelScope.launch {
        if (content.isBlank()) return@launch
        chatRepo.sendMessage(content.trim(), replyToId, replyToContent)
            .onFailure { _uiState.value = SimpleUiState.Error(it.message ?: "Gửi tin thất bại") }
    }

    fun deleteMessage(messageId: String) = viewModelScope.launch {
        chatRepo.deleteMessage(messageId)
            .onSuccess { _uiState.value = SimpleUiState.Success("Đã xóa tin nhắn") }
            .onFailure { _uiState.value = SimpleUiState.Error(it.message ?: "Xóa thất bại") }
    }

    fun clearUiState() { _uiState.value = SimpleUiState.Idle }
}

// ─────────────────────────────────────────────────────────────
// PROMPT VIEW MODEL
// Prompt sharing forum with like/unlike toggle
// ─────────────────────────────────────────────────────────────

@HiltViewModel
class PromptViewModel @Inject constructor(
    private val promptRepo: PromptRepository,
    private val userRepo: UserRepository
) : ViewModel() {

    val prompts: StateFlow<List<Prompt>> = promptRepo.observePrompts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentUserId: String? get() = userRepo.currentUserId
    val isLoggedIn: Boolean get() = userRepo.isLoggedIn

    private val _uiState = MutableStateFlow<SimpleUiState>(SimpleUiState.Idle)
    val uiState: StateFlow<SimpleUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredPrompts: StateFlow<List<Prompt>> = combine(prompts, _searchQuery) { list, q ->
        if (q.isBlank()) list
        else list.filter {
            it.title.contains(q, ignoreCase = true) ||
            it.authorName.contains(q, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchQueryChange(q: String) { _searchQuery.value = q }

    fun createPrompt(title: String, content: String) = viewModelScope.launch {
        if (title.isBlank() || content.isBlank()) {
            _uiState.value = SimpleUiState.Error("Vui lòng điền đầy đủ tiêu đề và nội dung")
            return@launch
        }
        _uiState.value = SimpleUiState.Loading
        promptRepo.createPrompt(title, content)
            .onSuccess { _uiState.value = SimpleUiState.Success("Đăng prompt thành công!") }
            .onFailure { _uiState.value = SimpleUiState.Error(it.message ?: "Đăng thất bại") }
    }

    fun toggleLike(promptId: String) = viewModelScope.launch {
        promptRepo.toggleLike(promptId)
            .onFailure { _uiState.value = SimpleUiState.Error(it.message ?: "Thao tác thất bại") }
    }

    fun deletePrompt(promptId: String) = viewModelScope.launch {
        promptRepo.deletePrompt(promptId)
            .onSuccess { _uiState.value = SimpleUiState.Success("Đã xóa prompt") }
            .onFailure { _uiState.value = SimpleUiState.Error(it.message ?: "Xóa thất bại") }
    }

    fun clearUiState() { _uiState.value = SimpleUiState.Idle }
}

// ─────────────────────────────────────────────────────────────
// SHARED BOOK VIEW MODEL
// Upload .txt to external host → store URL in RTDB
// ─────────────────────────────────────────────────────────────

@HiltViewModel
class SharedBookViewModel @Inject constructor(
    private val sharedBookRepo: SharedBookRepository,
    private val bookRepo: com.example.novelreader.domain.repository.BookRepository,
    private val userRepo: UserRepository,
    private val uploadService: ExternalFileUploadService
) : ViewModel() {

    val books: StateFlow<List<SharedNovel>> = sharedBookRepo.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isLoggedIn: Boolean get() = userRepo.isLoggedIn

    private val _uiState = MutableStateFlow<SharedBookUiState>(SharedBookUiState.Idle)
    val uiState: StateFlow<SharedBookUiState> = _uiState.asStateFlow()

    // ─── Upload flow: file bytes → external URL → RTDB ────────

    /**
     * Upload a TXT file to external hosting then register it in RTDB.
     *
     * @param title     Display title shown in the list.
     * @param fileName  Original file name (e.g. "novel.txt").
     * @param fileBytes Raw bytes of the .txt file.
     */
    fun uploadAndShare(title: String, fileName: String, fileBytes: ByteArray) =
        viewModelScope.launch {
            if (title.isBlank()) {
                _uiState.value = SharedBookUiState.Error("Tên truyện không được để trống")
                return@launch
            }
            _uiState.value = SharedBookUiState.Uploading

            // Step 1: Upload file to external host
            uploadService.upload(fileName, fileBytes)
                .mapCatching { externalUrl ->
                    // Step 2: Register metadata + URL in RTDB
                    sharedBookRepo.addBook(
                        title       = title,
                        externalUrl = externalUrl,
                        fileSize    = fileBytes.size.toLong()
                    ).getOrThrow()
                }
                .onSuccess { _uiState.value = SharedBookUiState.Success("Chia sẻ thành công! 🎉") }
                .onFailure { _uiState.value = SharedBookUiState.Error(it.message ?: "Chia sẻ thất bại") }
        }

    /**
     * Register a book using a manually-provided external URL.
     * Use this when the file is already hosted (e.g. Google Drive, Mega).
     */
    fun shareWithManualUrl(title: String, externalUrl: String, fileSize: Long = 0L) =
        viewModelScope.launch {
            if (title.isBlank() || externalUrl.isBlank()) {
                _uiState.value = SharedBookUiState.Error("Tên và URL không được để trống")
                return@launch
            }
            _uiState.value = SharedBookUiState.Uploading
            sharedBookRepo.addBook(title, externalUrl, fileSize)
                .onSuccess { _uiState.value = SharedBookUiState.Success("Chia sẻ thành công!") }
                .onFailure { _uiState.value = SharedBookUiState.Error(it.message ?: "Chia sẻ thất bại") }
        }

    // ─── Download → import to local bookshelf ─────────────────

    /**
     * Download a shared novel from the external URL and import it
     * into the user's local bookshelf via the Room-backed BookRepository.
     */
    fun downloadAndImport(novel: SharedNovel) = viewModelScope.launch {
        _uiState.value = SharedBookUiState.Downloading(novel.title)

        runCatching {
            // Fetch raw bytes from the external URL
            val url = java.net.URL(novel.fileUrl)
            val text = url.openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 60_000
            }.getInputStream().bufferedReader(Charsets.UTF_8).readText()

            // Import into Room
            bookRepo.importTxtFile(text, novel.title, null).getOrThrow()
        }
            .onSuccess {
                // Increment the download counter in RTDB
                sharedBookRepo.incrementDownload(novel.id)
                _uiState.value = SharedBookUiState.Success("Đã thêm \"${novel.title}\" vào kệ sách!")
            }
            .onFailure { _uiState.value = SharedBookUiState.Error(it.message ?: "Tải thất bại") }
    }

    fun deleteBook(bookId: String) = viewModelScope.launch {
        sharedBookRepo.deleteBook(bookId)
            .onSuccess { _uiState.value = SharedBookUiState.Success("Đã xóa truyện") }
            .onFailure { _uiState.value = SharedBookUiState.Error(it.message ?: "Xóa thất bại") }
    }

    fun clearUiState() { _uiState.value = SharedBookUiState.Idle }
}

// ─────────────────────────────────────────────────────────────
// UI STATE TYPES
// ─────────────────────────────────────────────────────────────

/** Reusable simple loading/success/error state. */
sealed class SimpleUiState {
    object Idle    : SimpleUiState()
    object Loading : SimpleUiState()
    data class Success(val message: String) : SimpleUiState()
    data class Error(val message: String)   : SimpleUiState()
}

sealed class AuthUiState {
    object Idle             : AuthUiState()
    object Loading          : AuthUiState()
    object LoggedOut        : AuthUiState()
    object PasswordResetSent: AuthUiState()
    data class LoggedIn(val user: User) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

sealed class SharedBookUiState {
    object Idle : SharedBookUiState()
    object Uploading : SharedBookUiState()
    data class Downloading(val title: String) : SharedBookUiState()
    data class Success(val message: String) : SharedBookUiState()
    data class Error(val message: String) : SharedBookUiState()
}
