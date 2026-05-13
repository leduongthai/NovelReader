package com.example.novelreader.domain.repository

import com.example.novelreader.domain.model.*
import kotlinx.coroutines.flow.Flow

// ============================================================
// FIREBASE DOMAIN INTERFACES
// Complements the existing BookRepository / ChapterRepository
// in Interfaces.kt. These four interfaces cover the Firebase
// backend features: users, global chat, prompts, shared books.
// ============================================================

// ─────────────────────────────────────────────────────────────
// USER REPOSITORY
// ─────────────────────────────────────────────────────────────

interface UserRepository {

    /** UID of the currently signed-in user, or null. */
    val currentUserId: String?

    /** True if a user session is active. */
    val isLoggedIn: Boolean

    // ── Auth ──────────────────────────────────────────────────

    /** Create a new account and write the profile to RTDB. */
    suspend fun signUp(name: String, email: String, password: String): Result<User>

    /** Authenticate with email/password; checks ban status before returning. */
    suspend fun signIn(email: String, password: String): Result<User>

    /** Sign out the current session. */
    suspend fun signOut()

    /** Send a password-reset email. */
    suspend fun sendPasswordReset(email: String): Result<Unit>

    // ── Profile ───────────────────────────────────────────────

    /**
     * Fetch the current user's full profile from RTDB (single read).
     * Returns null when not signed in or profile doesn't exist.
     */
    suspend fun fetchCurrentUser(): User?

    /**
     * Real-time stream of the current user's profile.
     * Emits null when signed out.
     */
    fun observeCurrentUser(): Flow<User?>

    /** Update the current user's display name and optional avatar URL. */
    suspend fun updateProfile(name: String, avatarUrl: String): Result<Unit>

    // ── Admin / Mod ───────────────────────────────────────────

    /**
     * Real-time stream of all user summaries (for Admin panel).
     * Only Mod / Admin should call this; the RTDB rules allow any
     * authenticated user to read the users node (needed for role badges),
     * so enforce this check in the ViewModel layer.
     */
    fun observeAllUsers(): Flow<List<UserSummary>>

    /**
     * Promote or demote a user's role.
     * Requires ADMIN privilege.
     */
    suspend fun updateRole(targetUid: String, newRole: UserRole): Result<Unit>

    /**
     * Ban a user.
     * [durationHours] = 0 → permanent.
     * MOD may ban USER. ADMIN may ban USER or MOD.
     */
    suspend fun banUser(targetUid: String, reason: String, durationHours: Long = 0L): Result<Unit>

    /** Remove a ban. Requires MOD or ADMIN privilege. */
    suspend fun unbanUser(targetUid: String): Result<Unit>
}

// ─────────────────────────────────────────────────────────────
// CHAT REPOSITORY
// ─────────────────────────────────────────────────────────────

interface ChatRepository {

    /**
     * Real-time stream of the last [limit] global chat messages,
     * already filtered to exclude soft-deleted entries.
     */
    fun observeMessages(limit: Int = 100): Flow<List<ChatMessage>>

    /**
     * Send a message to the global chat room.
     * Verifies ban status before writing.
     * Supports reply threading via [replyToId] / [replyToContent].
     */
    suspend fun sendMessage(
        content: String,
        replyToId: String? = null,
        replyToContent: String? = null
    ): Result<Unit>

    /**
     * Soft-delete a message (sets isDeleted = true).
     * Allowed for: message owner, MOD, ADMIN.
     */
    suspend fun deleteMessage(messageId: String): Result<Unit>
}

// ─────────────────────────────────────────────────────────────
// PROMPT REPOSITORY
// ─────────────────────────────────────────────────────────────

interface PromptRepository {

    /** Real-time stream of prompts, sorted by likes descending. */
    fun observePrompts(): Flow<List<Prompt>>

    /**
     * Post a new prompt.
     * Verifies ban status before writing.
     */
    suspend fun createPrompt(title: String, content: String): Result<Unit>

    /**
     * Toggle the current user's like on a prompt.
     * Uses an atomic RTDB transaction — no double-counting.
     */
    suspend fun toggleLike(promptId: String): Result<Unit>

    /**
     * Delete a prompt.
     * Allowed for: prompt author, MOD, ADMIN.
     */
    suspend fun deletePrompt(promptId: String): Result<Unit>
}

// ─────────────────────────────────────────────────────────────
// SHARED BOOK REPOSITORY
// ─────────────────────────────────────────────────────────────

/**
 * Stores only metadata + an external download URL.
 * File bytes NEVER touch Firebase (free-tier constraint).
 *
 * Upload flow:
 *   1. App calls [ExternalFileUploadService.upload] → gets a URL.
 *   2. App calls [addBook] with that URL → metadata written to RTDB.
 */
interface SharedBookRepository {

    /** Real-time stream of shared books, newest first. */
    fun observeBooks(): Flow<List<SharedNovel>>

    /**
     * Register a shared book with a pre-obtained external URL.
     *
     * @param title       Display title
     * @param externalUrl Direct download URL from the external host
     * @param fileSize    File size in bytes (for display; caller provides it)
     */
    suspend fun addBook(title: String, externalUrl: String, fileSize: Long): Result<SharedNovel>

    /**
     * Atomically increment the download counter for a book.
     * Call this when a user taps "Download".
     */
    suspend fun incrementDownload(bookId: String): Result<Unit>

    /**
     * Delete a shared book entry.
     * Allowed for: uploader, MOD, ADMIN.
     */
    suspend fun deleteBook(bookId: String): Result<Unit>
}
