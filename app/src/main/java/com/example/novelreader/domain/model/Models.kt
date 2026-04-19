package com.example.novelreader.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// ============================================================
// DOMAIN MODELS — Pure Kotlin, no Android/Room dependencies
// These are the "truth" objects passed between layers
// ============================================================

/**
 * Represents a novel/book in the system.
 * Source can be: "local" (TXT import), "crawl" (web), "shared" (community download)
 */
@Parcelize
data class Book(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val source: String = "local",       // "local" | "crawl" | "shared"
    val sourceUrl: String = "",          // Original URL if crawled
    val totalChapters: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
) : Parcelable

/**
 * A single chapter belonging to a book.
 * Content may be empty if not yet loaded (lazy loading from web).
 */
@Parcelize
data class Chapter(
    val id: String = "",
    val bookId: String = "",
    val title: String = "",
    val content: String = "",
    val translatedContent: String = "",  // AI-translated version
    val chapterIndex: Int = 0,
    val sourceUrl: String = "",           // URL to fetch content if empty
    val isLoaded: Boolean = false,
    val isBookmarked: Boolean = false
) : Parcelable

/**
 * Reading progress for a specific book.
 */
data class ReadingHistory(
    val id: String = "",
    val bookId: String = "",
    val chapterId: String = "",
    val chapterIndex: Int = 0,
    val scrollPosition: Float = 0f,      // 0.0 - 1.0 percentage
    val lastReadAt: Long = System.currentTimeMillis()
)

/**
 * Community prompt shared by users.
 */
data class Prompt(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val likes: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isLikedByMe: Boolean = false
)

/**
 * User account information.
 */
data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val isPremium: Boolean = false,
    val role: String = "user",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A community chat message.
 */
data class ChatMessage(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userAvatar: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val replyToId: String? = null,       // Optional: quoted message id
    val replyToContent: String? = null    // Snippet of quoted message
)

/**
 * A shared TXT novel file on community.
 */
data class SharedNovel(
    val id: String = "",
    val title: String = "",
    val uploaderName: String = "",
    val uploaderId: String = "",
    val fileUrl: String = "",
    val fileSize: Long = 0,
    val downloadCount: Int = 0,
    val uploadedAt: Long = System.currentTimeMillis()
)

/**
 * User reading preferences stored in DataStore.
 */
data class ReaderSettings(
    val fontSize: Float = 18f,
    val fontFamily: String = "default",   // "default" | "serif" | "monospace"
    val lineSpacing: Float = 1.6f,
    val backgroundColor: ReaderBackground = ReaderBackground.PAPER,
    val isDarkMode: Boolean = false,
    val geminiApiKey: String = "",
    val translationPrompt: String = DEFAULT_TRANSLATION_PROMPT,
    val ttsSpeed: Float = 1.0f
)

data class Review(
    val id: String = "",
    val bookId: String = "",
    val sourceUrl: String = "",
    val userId: String = "",
    val userName: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class ReaderBackground(val bg: Long, val text: Long) {
    WHITE(0xFFFFFFFF, 0xFF1A1A1A),
    PAPER(0xFFF5EED6, 0xFF2C2010),   // Sepia / cream — default
    GREEN(0xFFCCE8CC, 0xFF0D2B0D),
    DARK(0xFF1A1A2E, 0xFFE0E0E0),
    BLACK(0xFF000000, 0xFFCCCCCC)
}

data class ChatGroup(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val adminId: String = "",
    val members: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

// Default translation prompt (Vietnamese)
const val DEFAULT_TRANSLATION_PROMPT = """Bạn là một dịch giả chuyên nghiệp chuyên dịch truyện từ tiếng Trung/Anh/Nhật sang tiếng Việt.

Yêu cầu:
- Dịch toàn bộ nội dung được cung cấp sang tiếng Việt tự nhiên, mượt mà.
- Giữ nguyên tên riêng, địa danh, thuật ngữ đặc thù của thể loại.
- Bảo toàn giọng văn, cảm xúc và phong cách của tác giả.
- Không thêm hay bớt nội dung.
- Chỉ trả về bản dịch, không kèm giải thích hay chú thích.

Nội dung cần dịch:"""