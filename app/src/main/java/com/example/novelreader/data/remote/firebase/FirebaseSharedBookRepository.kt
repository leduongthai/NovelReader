package com.example.novelreader.data.remote.firebase

import com.example.novelreader.domain.model.*
import com.example.novelreader.domain.repository.SharedBookRepository
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
// FIREBASE SHARED BOOK REPOSITORY
//
// FREE-TIER DESIGN:
//   • File bytes NEVER enter Firebase (no Storage, no RTDB content).
//   • Only metadata + a pre-obtained external download URL are stored.
//   • Callers upload the file via ExternalFileUploadService first,
//     then pass the returned URL to addBook().
// ============================================================

@Singleton
class FirebaseSharedBookRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth,
    private val userRepo: FirebaseUserRepository
) : SharedBookRepository {

    companion object {
        private const val SHARED_NOVELS_NODE = "community/shared_novels"
    }

    private fun novelsRef() = database.reference.child(SHARED_NOVELS_NODE)

    // ─────────────────────────────────────────────────────────
    // Observe
    // ─────────────────────────────────────────────────────────

    override fun observeBooks(): Flow<List<SharedNovel>> = callbackFlow {
        val ref = novelsRef().orderByChild("uploadedAt")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children
                    .mapNotNull { it.toSharedNovel() }
                    .sortedByDescending { it.uploadedAt }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ─────────────────────────────────────────────────────────
    // Add (metadata only — caller provides the external URL)
    // ─────────────────────────────────────────────────────────

    override suspend fun addBook(
        title: String,
        externalUrl: String,
        fileSize: Long
    ): Result<SharedNovel> = runCatching {
        val user = auth.currentUser ?: throw Exception("Bạn cần đăng nhập để chia sẻ truyện")
        require(title.isNotBlank()) { "Tên truyện không được để trống" }
        require(externalUrl.isNotBlank()) { "URL file không được để trống" }
        require(externalUrl.startsWith("http")) { "URL không hợp lệ" }

        val profile = userRepo.fetchCurrentUser()
        if (profile?.banInfo?.isActive == true)
            throw Exception("Tài khoản bị khóa: ${profile.banInfo.reason}")

        val id = UUID.randomUUID().toString()
        val novel = SharedNovel(
            id            = id,
            title         = title.trim(),
            uploaderName  = profile?.name ?: "Ẩn danh",
            uploaderId    = user.uid,
            uploaderAvatar = profile?.avatarUrl ?: "",
            fileUrl       = externalUrl.trim(),
            fileSize      = fileSize,
            downloadCount = 0,
            uploadedAt    = System.currentTimeMillis()
        )
        novelsRef().child(id).setValue(novel.toFirebaseMap()).await()
        novel
    }

    // ─────────────────────────────────────────────────────────
    // Increment download counter (atomic)
    // ─────────────────────────────────────────────────────────

    override suspend fun incrementDownload(bookId: String): Result<Unit> = runCatching {
        auth.currentUser ?: throw Exception("Bạn cần đăng nhập")
        novelsRef().child(bookId).child("downloadCount")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    data.value = (data.getValue(Int::class.java) ?: 0) + 1
                    return Transaction.success(data)
                }
                override fun onComplete(
                    error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?
                ) {}
            })
    }

    // ─────────────────────────────────────────────────────────
    // Delete
    // ─────────────────────────────────────────────────────────

    override suspend fun deleteBook(bookId: String): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Bạn cần đăng nhập")

        val snap = novelsRef().child(bookId).get().await()
        val uploaderId = snap.child("uploaderId").getValue(String::class.java) ?: ""
        val callerRole = userRepo.fetchCurrentUser()?.userRole ?: UserRole.USER

        val canDelete = uid == uploaderId || callerRole == UserRole.MOD || callerRole == UserRole.ADMIN
        if (!canDelete) throw SecurityException("Bạn không có quyền xóa truyện này")

        novelsRef().child(bookId).removeValue().await()
    }

    // ─────────────────────────────────────────────────────────
    // Mapper
    // ─────────────────────────────────────────────────────────

    private fun DataSnapshot.toSharedNovel(): SharedNovel? = runCatching {
        SharedNovel(
            id            = child("id").getValue(String::class.java) ?: key ?: "",
            title         = child("title").getValue(String::class.java) ?: "",
            uploaderName  = child("uploaderName").getValue(String::class.java) ?: "",
            uploaderId    = child("uploaderId").getValue(String::class.java) ?: "",
            uploaderAvatar = child("uploaderAvatar").getValue(String::class.java) ?: "",
            fileUrl       = child("fileUrl").getValue(String::class.java) ?: "",
            fileSize      = child("fileSize").getValue(Long::class.java) ?: 0L,
            downloadCount = child("downloadCount").getValue(Int::class.java) ?: 0,
            uploadedAt    = child("uploadedAt").getValue(Long::class.java) ?: 0L
        )
    }.getOrNull()

    private fun SharedNovel.toFirebaseMap() = mapOf(
        "id"            to id,
        "title"         to title,
        "uploaderName"  to uploaderName,
        "uploaderId"    to uploaderId,
        "uploaderAvatar" to uploaderAvatar,
        "fileUrl"       to fileUrl,
        "fileSize"      to fileSize,
        "downloadCount" to downloadCount,
        "uploadedAt"    to uploadedAt
    )
}
