package com.example.novelreader.data.remote.firebase

import com.example.novelreader.domain.model.*
import com.example.novelreader.domain.repository.PromptRepository
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
// FIREBASE PROMPT REPOSITORY
// Prompt sharing with an atomic like/unlike toggle.
// likedBy/{uid} node prevents double-counting without Cloud Functions.
// ============================================================

@Singleton
class FirebasePromptRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth,
    private val userRepo: FirebaseUserRepository
) : PromptRepository {

    companion object {
        private const val PROMPTS_NODE = "community/prompts"
    }

    private fun promptsRef() = database.reference.child(PROMPTS_NODE)

    // ─────────────────────────────────────────────────────────
    // Observe
    // ─────────────────────────────────────────────────────────

    override fun observePrompts(): Flow<List<Prompt>> = callbackFlow {
        // Order by likes so Firebase index is used on the server side.
        // Add ".indexOn": ["likes"] to RTDB rules for efficiency.
        val ref = promptsRef().orderByChild("likes")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentUid = auth.currentUser?.uid
                val prompts = snapshot.children
                    .mapNotNull { it.toPrompt(currentUid) }
                    .sortedByDescending { it.likes }
                trySend(prompts)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ─────────────────────────────────────────────────────────
    // Create
    // ─────────────────────────────────────────────────────────

    override suspend fun createPrompt(title: String, content: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: throw Exception("Bạn cần đăng nhập để đăng prompt")
        require(title.isNotBlank()) { "Tiêu đề không được để trống" }
        require(content.isNotBlank()) { "Nội dung prompt không được để trống" }

        val profile = userRepo.fetchCurrentUser()
        if (profile?.banInfo?.isActive == true)
            throw Exception("Tài khoản bị khóa: ${profile.banInfo.reason}")

        val id = UUID.randomUUID().toString()
        val prompt = mapOf(
            "id"         to id,
            "title"      to title.trim(),
            "content"    to content.trim(),
            "authorId"   to user.uid,
            "authorAvatar" to (profile?.avatarUrl ?: ""),
            "authorName" to (profile?.name ?: "Ẩn danh"),
            "likes"      to 0,
            "likedBy"    to emptyMap<String, Boolean>(),
            "createdAt"  to System.currentTimeMillis()
        )
        promptsRef().child(id).setValue(prompt).await()
    }

    // ─────────────────────────────────────────────────────────
    // Toggle Like (atomic, prevents double-counting)
    // ─────────────────────────────────────────────────────────

    override suspend fun toggleLike(promptId: String): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Bạn cần đăng nhập để thích prompt")

        val likedByRef = promptsRef().child(promptId).child("likedBy").child(uid)
        val likesRef   = promptsRef().child(promptId).child("likes")

        // Check if already liked
        val alreadyLiked = likedByRef.get().await().getValue(Boolean::class.java) ?: false

        if (alreadyLiked) {
            // Unlike: decrement likes, remove uid from likedBy
            likedByRef.removeValue().await()
            likesRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    val current = data.getValue(Int::class.java) ?: 0
                    data.value = maxOf(0, current - 1)   // guard against negative
                    return Transaction.success(data)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
            })
        } else {
            // Like: increment likes, add uid to likedBy
            likedByRef.setValue(true).await()
            likesRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    data.value = (data.getValue(Int::class.java) ?: 0) + 1
                    return Transaction.success(data)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
            })
        }
    }

    // ─────────────────────────────────────────────────────────
    // Delete
    // ─────────────────────────────────────────────────────────

    override suspend fun deletePrompt(promptId: String): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Bạn cần đăng nhập")

        val snap = promptsRef().child(promptId).get().await()
        val authorId = snap.child("authorId").getValue(String::class.java) ?: ""
        val callerRole = userRepo.fetchCurrentUser()?.userRole ?: UserRole.USER

        val canDelete = uid == authorId || callerRole == UserRole.MOD || callerRole == UserRole.ADMIN
        if (!canDelete) throw SecurityException("Bạn không có quyền xóa bài viết này")

        promptsRef().child(promptId).removeValue().await()
    }

    // ─────────────────────────────────────────────────────────
    // Mapper
    // ─────────────────────────────────────────────────────────

    private fun DataSnapshot.toPrompt(currentUid: String?): Prompt? = runCatching {
        Prompt(
            id           = child("id").getValue(String::class.java) ?: key ?: "",
            title        = child("title").getValue(String::class.java) ?: "",
            content      = child("content").getValue(String::class.java) ?: "",
            authorId     = child("authorId").getValue(String::class.java) ?: "",
            authorAvatar = child("authorAvatar").getValue(String::class.java) ?: "",
            authorName   = child("authorName").getValue(String::class.java) ?: "",
            likes        = child("likes").getValue(Int::class.java) ?: 0,
            createdAt    = child("createdAt").getValue(Long::class.java) ?: 0L,
            isLikedByMe  = currentUid != null &&
                           child("likedBy").child(currentUid).getValue(Boolean::class.java) == true
        )
    }.getOrNull()
}
