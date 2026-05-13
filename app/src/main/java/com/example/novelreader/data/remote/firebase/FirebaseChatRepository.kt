package com.example.novelreader.data.remote.firebase

import com.example.novelreader.domain.model.*
import com.example.novelreader.domain.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================
// FIREBASE CHAT REPOSITORY
// Global chat room — real-time, last-100 messages, soft-delete
// ============================================================

@Singleton
class FirebaseChatRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth,
    // Inject UserRepository to reuse fetchCurrentUser / ban checks
    private val userRepo: FirebaseUserRepository
) : ChatRepository {

    companion object {
        private const val CHAT_NODE = "chat/messages"
        private const val DEFAULT_LIMIT = 100
    }

    private fun chatRef() = database.reference.child(CHAT_NODE)

    // ─────────────────────────────────────────────────────────
    // Observe
    // ─────────────────────────────────────────────────────────

    override fun observeMessages(limit: Int): Flow<List<ChatMessage>> = callbackFlow {
        val ref = chatRef()
            .orderByChild("timestamp")
            .limitToLast(limit.coerceIn(1, 200))

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children
                    .mapNotNull { it.toChatMessage() }
                    .filter { !it.isDeleted }        // hide soft-deleted messages
                trySend(messages)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ─────────────────────────────────────────────────────────
    // Send
    // ─────────────────────────────────────────────────────────

    override suspend fun sendMessage(
        content: String,
        replyToId: String?,
        replyToContent: String?
    ): Result<Unit> = runCatching {
        val user = auth.currentUser ?: throw Exception("Bạn cần đăng nhập để chat")
        require(content.isNotBlank()) { "Tin nhắn không được để trống" }

        // Ban check — re-fetches user profile (lightweight read on banInfo node)
        val profile = userRepo.fetchCurrentUser()
        if (profile?.banInfo?.isActive == true)
            throw Exception("Tài khoản bị khóa: ${profile.banInfo.reason}")

        val msgId = UUID.randomUUID().toString()
        val message = mapOf(
            "id"             to msgId,
            "userId"         to user.uid,
            "userName"       to (profile?.name ?: user.displayName ?: "Ẩn danh"),
            "userAvatar"     to (profile?.avatarUrl ?: ""),
            "userRole"       to (profile?.role ?: UserRole.USER.label),
            "content"        to content.trim(),
            "timestamp"      to System.currentTimeMillis(),
            "replyToId"      to replyToId,
            "replyToContent" to replyToContent,
            "isDeleted"      to false
        )
        chatRef().child(msgId).setValue(message).await()
    }

    // ─────────────────────────────────────────────────────────
    // Delete (soft)
    // ─────────────────────────────────────────────────────────

    override suspend fun deleteMessage(messageId: String): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Bạn cần đăng nhập")

        val msgSnap = chatRef().child(messageId).get().await()
        val ownerId = msgSnap.child("userId").getValue(String::class.java) ?: ""
        val callerRole = userRepo.fetchCurrentUser()?.userRole ?: UserRole.USER

        val canDelete = uid == ownerId || callerRole == UserRole.MOD || callerRole == UserRole.ADMIN
        if (!canDelete) throw SecurityException("Bạn không có quyền xóa tin nhắn này")

        chatRef().child(messageId).child("isDeleted").setValue(true).await()
    }

    // ─────────────────────────────────────────────────────────
    // Mapper
    // ─────────────────────────────────────────────────────────

    private fun DataSnapshot.toChatMessage(): ChatMessage? = runCatching {
        ChatMessage(
            id             = child("id").getValue(String::class.java) ?: key ?: "",
            userId         = child("userId").getValue(String::class.java) ?: "",
            userName       = child("userName").getValue(String::class.java) ?: "Ẩn danh",
            userAvatar     = child("userAvatar").getValue(String::class.java) ?: "",
            userRole       = child("userRole").getValue(String::class.java) ?: UserRole.USER.label,
            content        = child("content").getValue(String::class.java) ?: "",
            timestamp      = child("timestamp").getValue(Long::class.java) ?: 0L,
            replyToId      = child("replyToId").getValue(String::class.java),
            replyToContent = child("replyToContent").getValue(String::class.java),
            isDeleted      = child("isDeleted").getValue(Boolean::class.java) ?: false
        )
    }.getOrNull()
}
