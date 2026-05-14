package com.example.novelreader.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// ============================================================
// DOMAIN MODELS — Pure Kotlin, no Android/Room dependencies
// ============================================================

@Parcelize
data class Book(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val source: String = "local",
    val sourceUrl: String = "",
    val totalChapters: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
) : Parcelable

@Parcelize
data class Chapter(
    val id: String = "",
    val bookId: String = "",
    val title: String = "",
    val content: String = "",
    val translatedContent: String = "",
    val chapterIndex: Int = 0,
    val sourceUrl: String = "",
    val isLoaded: Boolean = false,
    val isBookmarked: Boolean = false
) : Parcelable

data class ReadingHistory(
    val id: String = "",
    val bookId: String = "",
    val chapterId: String = "",
    val chapterIndex: Int = 0,
    val scrollPosition: Float = 0f,
    val lastReadAt: Long = System.currentTimeMillis()
)

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

// ============================================================
// PHÂN QUYỀN NGƯỜI DÙNG
// ============================================================

/**
 * Ba cấp độ tài khoản:
 *  - USER  : người dùng thông thường — chat, đăng bài, chia sẻ prompt
 *  - MOD   : quản lý nội dung — xóa bài/tin nhắn, cảnh báo/ban user
 *  - ADMIN : toàn quyền — mọi thứ Mod làm + thăng/hạ cấp Mod, xóa tài khoản
 */
enum class UserRole(val label: String, val displayName: String) {
    USER("user", "Người dùng"),
    MOD("mod", "Kiểm duyệt viên"),
    ADMIN("admin", "Quản trị viên");

    companion object {
        fun fromString(value: String?): UserRole =
            values().firstOrNull { it.label == value } ?: USER
    }
}

/** Thông tin ban tài khoản */
data class BanInfo(
    val isBanned: Boolean = false,
    val reason: String = "",
    val bannedBy: String = "",          // UID của mod/admin thực hiện
    val bannedByName: String = "",
    val bannedAt: Long = 0L,
    val expiresAt: Long = 0L            // 0 = vĩnh viễn
) {
    val isPermanent get() = expiresAt == 0L
    val isExpired get() = expiresAt > 0L && System.currentTimeMillis() > expiresAt
    val isActive get() = isBanned && !isExpired
}

/**
 * Tài khoản người dùng đầy đủ.
 */
data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val isPremium: Boolean = false,
    val role: String = UserRole.USER.label,
    val createdAt: Long = System.currentTimeMillis(),
    val banInfo: BanInfo = BanInfo()
) {
    val userRole: UserRole get() = UserRole.fromString(role)
    val isMod: Boolean get() = userRole == UserRole.MOD || userRole == UserRole.ADMIN
    val isAdmin: Boolean get() = userRole == UserRole.ADMIN
    val isBanned: Boolean get() = banInfo.isActive
}

/**
 * Bản rút gọn của User dùng trong danh sách Admin panel.
 * Tránh load toàn bộ dữ liệu khi chỉ cần hiển thị danh sách.
 */
data class UserSummary(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = UserRole.USER.label,
    val isBanned: Boolean = false,
    val banReason: String = "",
    val createdAt: Long = 0L
) {
    val userRole: UserRole get() = UserRole.fromString(role)
}

data class ChatMessage(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userAvatar: String = "",
    val userRole: String = UserRole.USER.label,   // hiển thị badge role trong chat
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val replyToId: String? = null,
    val replyToContent: String? = null,
    val isDeleted: Boolean = false                // soft-delete bởi mod/admin
)

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

data class CommunityComment(
    val id: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userAvatar: String = "",
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class ReaderSettings(
    val fontSize: Float = 18f,
    val fontFamily: String = "default",
    val lineSpacing: Float = 1.6f,
    val backgroundColor: ReaderBackground = ReaderBackground.PAPER,
    val isDarkMode: Boolean = false,
    val autoOpenLastBook: Boolean = false,
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
    PAPER(0xFFF5EED6, 0xFF2C2010),
    GREEN(0xFFCCE8CC, 0xFF0D2B0D),
    DARK(0xFF1A1A2E, 0xFFE0E0E0),
    BLACK(0xFF000000, 0xFFCCCCCC)
}

const val DEFAULT_TRANSLATION_PROMPT = """Bạn là một dịch giả chuyên nghiệp chuyên dịch truyện từ tiếng Trung/Anh/Nhật sang tiếng Việt.

Yêu cầu:
- Dịch toàn bộ nội dung được cung cấp sang tiếng Việt tự nhiên, mượt mà.
- Giữ nguyên tên riêng, địa danh, thuật ngữ đặc thù của thể loại.
- Bảo toàn giọng văn, cảm xúc và phong cách của tác giả.
- Không thêm hay bớt nội dung.
- Chỉ trả về bản dịch, không kèm giải thích hay chú thích.

Nội dung cần dịch:"""
