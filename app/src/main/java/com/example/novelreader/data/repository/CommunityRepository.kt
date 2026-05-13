package com.example.novelreader.data.repository

import com.example.novelreader.domain.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import android.util.Base64
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================
// FIREBASE COMMUNITY REPOSITORY
// Phân quyền: USER / MOD / ADMIN
// ============================================================

@Singleton
class CommunityRepository @Inject constructor(
    private val database: FirebaseDatabase,
    val auth: FirebaseAuth
) {

    companion object {
        private const val CHAT_NODE              = "chat/messages"
        private const val PROMPTS_NODE           = "community/prompts"
        private const val SHARED_NOVELS_NODE     = "community/shared_novels"
        private const val SHARED_NOVELS_CONTENT  = "community/shared_novels_content"  // nội dung riêng
        private const val USERS_NODE             = "users"
        private const val CHAT_MESSAGE_LIMIT     = 100
        private const val MAX_FILE_SIZE_BYTES    = 900_000  // ~900 KB — an toàn dưới giới hạn 1MB của RTDB
    }

    // =========================================================
    // AUTH HELPERS
    // =========================================================

    val currentUserId: String? get() = auth.currentUser?.uid
    val isLoggedIn: Boolean get() = auth.currentUser != null

    /** Lấy User đầy đủ từ DB (bao gồm role + banInfo) */
    suspend fun fetchCurrentUser(): User? {
        val uid = currentUserId ?: return null
        return fetchUser(uid)
    }

    private suspend fun fetchUser(uid: String): User? {
        val snap = database.reference.child(USERS_NODE).child(uid).get().await()
        return snap.toUser()
    }

    /** Lấy role của người dùng hiện tại */
    private suspend fun currentRole(): UserRole {
        val uid = currentUserId ?: return UserRole.USER
        val snap = database.reference.child(USERS_NODE).child(uid)
            .child("role").get().await()
        return UserRole.fromString(snap.getValue(String::class.java))
    }

    /** Kiểm tra user hiện tại có ít nhất role yêu cầu không */
    private suspend fun requireRole(minimum: UserRole) {
        val role = currentRole()
        val order = listOf(UserRole.USER, UserRole.MOD, UserRole.ADMIN)
        if (order.indexOf(role) < order.indexOf(minimum))
            throw Exception("Bạn không có quyền thực hiện thao tác này")
    }

    /** Kiểm tra user hiện tại có bị ban không */
    private suspend fun checkNotBanned() {
        val uid = currentUserId ?: return
        val snap = database.reference.child(USERS_NODE).child(uid)
            .child("banInfo").get().await()
        val banInfo = snap.toBanInfo()
        if (banInfo.isActive) throw Exception("Tài khoản của bạn đang bị khóa: ${banInfo.reason}")
    }

    // =========================================================
    // AUTH — Đăng ký / Đăng nhập / Đăng xuất
    // =========================================================

    suspend fun signUp(name: String, email: String, password: String): Result<User> =
        runCatching {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("Đăng ký thất bại")
            val user = User(id = uid, name = name, email = email, role = UserRole.USER.label)
            database.reference.child(USERS_NODE).child(uid)
                .setValue(user.toFirebaseMap()).await()
            user
        }

    suspend fun signIn(email: String, password: String): Result<User> = runCatching {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw Exception("Đăng nhập thất bại")
        val user = fetchUser(uid) ?: throw Exception("Không tìm thấy thông tin người dùng")
        if (user.banInfo.isActive)
            throw Exception("Tài khoản bị khóa: ${user.banInfo.reason}")
        user
    }

    suspend fun signOut() = auth.signOut()

    // =========================================================
    // CHAT — Tin nhắn cộng đồng
    // =========================================================

    fun getChatMessages(): Flow<List<ChatMessage>> = callbackFlow {
        val ref = database.reference
            .child(CHAT_NODE)
            .orderByChild("timestamp")
            .limitToLast(CHAT_MESSAGE_LIMIT)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children
                    .mapNotNull { it.toChatMessage() }
                    .filter { !it.isDeleted }   // Ẩn tin nhắn đã bị xóa
                trySend(messages)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun sendMessage(
        content: String,
        replyToId: String? = null,
        replyToContent: String? = null
    ): Result<Unit> = runCatching {
        val user = auth.currentUser ?: throw Exception("Bạn cần đăng nhập để chat")
        checkNotBanned()
        val userData = fetchUser(user.uid)
        val messageId = UUID.randomUUID().toString()
        val message = ChatMessage(
            id             = messageId,
            userId         = user.uid,
            userName       = userData?.name ?: user.displayName ?: "Ẩn danh",
            userAvatar     = userData?.avatarUrl ?: "",
            userRole       = userData?.role ?: UserRole.USER.label,
            content        = content.trim(),
            timestamp      = System.currentTimeMillis(),
            replyToId      = replyToId,
            replyToContent = replyToContent,
            isDeleted      = false
        )
        database.reference.child(CHAT_NODE).child(messageId)
            .setValue(message.toFirebaseMap()).await()
    }

    /**
     * Xóa tin nhắn chat (soft-delete).
     * Quyền: chủ tin nhắn, hoặc Mod/Admin.
     */
    suspend fun deleteChatMessage(messageId: String): Result<Unit> = runCatching {
        val uid = currentUserId ?: throw Exception("Bạn cần đăng nhập")
        val msgSnap = database.reference.child(CHAT_NODE).child(messageId).get().await()
        val msgOwnerId = msgSnap.child("userId").getValue(String::class.java) ?: ""
        val role = currentRole()
        if (uid != msgOwnerId && role == UserRole.USER)
            throw Exception("Bạn không có quyền xóa tin nhắn này")

        database.reference.child(CHAT_NODE).child(messageId)
            .child("isDeleted").setValue(true).await()
    }

    // =========================================================
    // PROMPT FORUM
    // =========================================================

    fun getPrompts(): Flow<List<Prompt>> = callbackFlow {
        val ref = database.reference.child(PROMPTS_NODE).orderByChild("likes")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val prompts = snapshot.children
                    .mapNotNull { it.toPrompt() }
                    .filter { it.id.isNotBlank() }
                    .sortedByDescending { it.likes }
                trySend(prompts)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun postPrompt(title: String, content: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: throw Exception("Bạn cần đăng nhập để đăng prompt")
        checkNotBanned()
        val userData = fetchUser(user.uid)
        val id = UUID.randomUUID().toString()
        val prompt = mapOf(
            "id"         to id,
            "title"      to title,
            "content"    to content,
            "authorId"   to user.uid,
            "authorName" to (userData?.name ?: "Ẩn danh"),
            "likes"      to 0,
            "createdAt"  to System.currentTimeMillis()
        )
        database.reference.child(PROMPTS_NODE).child(id).setValue(prompt).await()
    }

    suspend fun likePrompt(promptId: String): Result<Unit> = runCatching {
        auth.currentUser ?: throw Exception("Bạn cần đăng nhập để thích prompt")
        val ref = database.reference.child(PROMPTS_NODE).child(promptId).child("likes")
        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                data.value = (data.getValue(Int::class.java) ?: 0) + 1
                return Transaction.success(data)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
        })
    }

    /**
     * Xóa prompt. Quyền: chủ bài hoặc Mod/Admin.
     */
    suspend fun deletePrompt(promptId: String): Result<Unit> = runCatching {
        val uid = currentUserId ?: throw Exception("Bạn cần đăng nhập")
        val snap = database.reference.child(PROMPTS_NODE).child(promptId).get().await()
        val authorId = snap.child("authorId").getValue(String::class.java) ?: ""
        val role = currentRole()
        if (uid != authorId && role == UserRole.USER)
            throw Exception("Bạn không có quyền xóa bài viết này")
        database.reference.child(PROMPTS_NODE).child(promptId).removeValue().await()
    }

    // =========================================================
    // SHARED NOVELS
    // =========================================================

    // =========================================================
    // SHARED NOVELS — lưu trên Realtime Database (không cần Storage)
    // =========================================================

    fun getSharedNovels(): Flow<List<SharedNovel>> = callbackFlow {
        val ref = database.reference.child(SHARED_NOVELS_NODE).orderByChild("uploadedAt")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { it.toSharedNovel() }.sortedByDescending { it.uploadedAt })
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Chia sẻ file truyện — lưu metadata vào shared_novels, nội dung vào shared_novels_content.
     * Tách 2 node để danh sách không load toàn bộ nội dung.
     * Giới hạn ~900 KB — đủ cho hầu hết file .txt truyện chữ.
     */
    suspend fun shareNovelFile(title: String, fileBytes: ByteArray): Result<SharedNovel> = runCatching {
        val user = auth.currentUser ?: throw Exception("Bạn cần đăng nhập để chia sẻ truyện")
        checkNotBanned()

        if (fileBytes.size > MAX_FILE_SIZE_BYTES)
            throw Exception("File quá lớn (tối đa ~900 KB). Vui lòng chia nhỏ hoặc nén file.")

        val userData = fetchUser(user.uid)
        val id = UUID.randomUUID().toString()

        // Thử decode UTF-8 trước, nếu lỗi thử GBK
        val contentString = try {
            fileBytes.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            try { fileBytes.toString(Charsets.UTF_8) }
            catch (e2: Exception) { Base64.encodeToString(fileBytes, Base64.NO_WRAP) }
        }

        // Lưu nội dung riêng (không load khi xem danh sách)
        database.reference.child(SHARED_NOVELS_CONTENT).child(id)
            .setValue(
                mapOf(
                    "content" to contentString,
                    "encoding" to "utf8",
                    "uploaderId" to user.uid
                )
            ).await()

        val novel = SharedNovel(
            id           = id,
            title        = title,
            uploaderName = userData?.name ?: "Ẩn danh",
            uploaderId   = user.uid,
            fileUrl      = id,              // dùng id làm key tra cứu content
            fileSize     = fileBytes.size.toLong(),
            uploadedAt   = System.currentTimeMillis()
        )

        // Lưu metadata (hiển thị danh sách)
        database.reference.child(SHARED_NOVELS_NODE).child(id)
            .setValue(novel.toFirebaseMap()).await()

        novel
    }

    /**
     * Tải nội dung truyện đã chia sẻ từ RTDB.
     * CommunityViewModel gọi hàm này trước khi import.
     */
    suspend fun fetchSharedNovelContent(novelId: String): Result<String> = runCatching {
        val snap = database.reference.child(SHARED_NOVELS_CONTENT).child(novelId).get().await()
        snap.child("content").getValue(String::class.java)
            ?: throw Exception("Không tìm thấy nội dung truyện")
    }

    suspend fun incrementSharedNovelDownload(novelId: String): Result<Unit> = runCatching {
        database.reference.child(SHARED_NOVELS_NODE).child(novelId).child("downloadCount")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    data.value = (data.getValue(Int::class.java) ?: 0) + 1
                    return Transaction.success(data)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    snapshot: DataSnapshot?
                ) = Unit
            })
    }

    /**
     * Xóa truyện chia sẻ. Quyền: chủ bài hoặc Mod/Admin.
     */
    suspend fun deleteSharedNovel(novelId: String): Result<Unit> = runCatching {
        val uid = currentUserId ?: throw Exception("Bạn cần đăng nhập")
        val snap = database.reference.child(SHARED_NOVELS_NODE).child(novelId).get().await()
        val uploaderId = snap.child("uploaderId").getValue(String::class.java) ?: ""
        val role = currentRole()
        if (uid != uploaderId && role == UserRole.USER)
            throw Exception("Bạn không có quyền xóa truyện này")
        database.reference.child(SHARED_NOVELS_NODE).child(novelId).removeValue().await()
    }

    // =========================================================
    // ADMIN / MOD — Quản lý người dùng
    // =========================================================

    /**
     * Lấy danh sách tất cả người dùng (chỉ Admin/Mod).
     */
    fun getAllUsers(): Flow<List<UserSummary>> = callbackFlow {
        val ref = database.reference.child(USERS_NODE).orderByChild("createdAt")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { it.toUserSummary() }
                    .sortedByDescending { it.createdAt }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Khóa tài khoản người dùng.
     * MOD: chỉ có thể ban USER.
     * ADMIN: có thể ban USER và MOD.
     */
    suspend fun banUser(
        targetUid: String,
        reason: String,
        durationHours: Long = 0L    // 0 = vĩnh viễn
    ): Result<Unit> = runCatching {
        val actorUid = currentUserId ?: throw Exception("Bạn cần đăng nhập")
        val actorRole = currentRole()
        if (actorRole == UserRole.USER) throw Exception("Bạn không có quyền khóa tài khoản")

        val targetSnap = database.reference.child(USERS_NODE).child(targetUid).get().await()
        val targetRole = UserRole.fromString(targetSnap.child("role").getValue(String::class.java))
        if (targetRole == UserRole.ADMIN) throw Exception("Không thể khóa tài khoản Admin")
        if (targetRole == UserRole.MOD && actorRole != UserRole.ADMIN)
            throw Exception("Chỉ Admin mới có thể khóa tài khoản Mod")

        val actorData = fetchUser(actorUid)
        val expiresAt = if (durationHours > 0) System.currentTimeMillis() + durationHours * 3600_000L else 0L
        val banMap = mapOf(
            "isBanned"     to true,
            "reason"       to reason.trim(),
            "bannedBy"     to actorUid,
            "bannedByName" to (actorData?.name ?: "Kiểm duyệt viên"),
            "bannedAt"     to System.currentTimeMillis(),
            "expiresAt"    to expiresAt
        )
        database.reference.child(USERS_NODE).child(targetUid)
            .child("banInfo").setValue(banMap).await()
    }

    /**
     * Bỏ khóa tài khoản người dùng. Quyền: Mod/Admin.
     */
    suspend fun unbanUser(targetUid: String): Result<Unit> = runCatching {
        requireRole(UserRole.MOD)
        val banMap = mapOf(
            "isBanned"     to false,
            "reason"       to "",
            "bannedBy"     to "",
            "bannedByName" to "",
            "bannedAt"     to 0L,
            "expiresAt"    to 0L
        )
        database.reference.child(USERS_NODE).child(targetUid)
            .child("banInfo").setValue(banMap).await()
    }

    /**
     * Thăng cấp người dùng lên Mod. Chỉ Admin.
     */
    suspend fun promoteToMod(targetUid: String): Result<Unit> = runCatching {
        requireRole(UserRole.ADMIN)
        val targetSnap = database.reference.child(USERS_NODE).child(targetUid).get().await()
        val targetRole = UserRole.fromString(targetSnap.child("role").getValue(String::class.java))
        if (targetRole == UserRole.ADMIN) throw Exception("Không thể thay đổi quyền Admin")
        database.reference.child(USERS_NODE).child(targetUid)
            .child("role").setValue(UserRole.MOD.label).await()
    }

    /**
     * Hạ cấp Mod về User. Chỉ Admin.
     */
    suspend fun demoteToUser(targetUid: String): Result<Unit> = runCatching {
        requireRole(UserRole.ADMIN)
        val targetSnap = database.reference.child(USERS_NODE).child(targetUid).get().await()
        val targetRole = UserRole.fromString(targetSnap.child("role").getValue(String::class.java))
        if (targetRole == UserRole.ADMIN) throw Exception("Không thể hạ cấp tài khoản Admin")
        database.reference.child(USERS_NODE).child(targetUid)
            .child("role").setValue(UserRole.USER.label).await()
    }

    /**
     * Cập nhật thông tin cá nhân (tên, avatar).
     */
    suspend fun updateProfile(name: String, avatarUrl: String): Result<Unit> = runCatching {
        val uid = currentUserId ?: throw Exception("Bạn cần đăng nhập")
        val updates = mutableMapOf<String, Any>("name" to name.trim())
        if (avatarUrl.isNotBlank()) updates["avatarUrl"] = avatarUrl
        database.reference.child(USERS_NODE).child(uid).updateChildren(updates).await()
    }

    // =========================================================
    // FIREBASE SNAPSHOT MAPPERS
    // =========================================================

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

    private fun DataSnapshot.toPrompt(): Prompt? = runCatching {
        Prompt(
            id         = child("id").getValue(String::class.java) ?: key ?: "",
            title      = child("title").getValue(String::class.java) ?: "",
            content    = child("content").getValue(String::class.java) ?: "",
            authorId   = child("authorId").getValue(String::class.java) ?: "",
            authorName = child("authorName").getValue(String::class.java) ?: "",
            likes      = child("likes").getValue(Int::class.java) ?: 0,
            createdAt  = child("createdAt").getValue(Long::class.java) ?: 0L
        )
    }.getOrNull()

    private fun DataSnapshot.toSharedNovel(): SharedNovel? = runCatching {
        SharedNovel(
            id            = child("id").getValue(String::class.java) ?: key ?: "",
            title         = child("title").getValue(String::class.java) ?: "",
            uploaderName  = child("uploaderName").getValue(String::class.java) ?: "",
            uploaderId    = child("uploaderId").getValue(String::class.java) ?: "",
            fileUrl       = child("fileUrl").getValue(String::class.java) ?: "",
            fileSize      = child("fileSize").getValue(Long::class.java) ?: 0L,
            downloadCount = child("downloadCount").getValue(Int::class.java) ?: 0,
            uploadedAt    = child("uploadedAt").getValue(Long::class.java) ?: 0L
        )
    }.getOrNull()

    private fun DataSnapshot.toUser(): User? = runCatching {
        User(
            id        = child("id").getValue(String::class.java) ?: key ?: "",
            name      = child("name").getValue(String::class.java) ?: "",
            email     = child("email").getValue(String::class.java) ?: "",
            avatarUrl = child("avatarUrl").getValue(String::class.java) ?: "",
            isPremium = child("isPremium").getValue(Boolean::class.java) ?: false,
            role      = child("role").getValue(String::class.java) ?: UserRole.USER.label,
            createdAt = child("createdAt").getValue(Long::class.java) ?: 0L,
            banInfo   = child("banInfo").toBanInfo()
        )
    }.getOrNull()

    private fun DataSnapshot.toUserSummary(): UserSummary? = runCatching {
        UserSummary(
            id        = child("id").getValue(String::class.java) ?: key ?: "",
            name      = child("name").getValue(String::class.java) ?: "",
            email     = child("email").getValue(String::class.java) ?: "",
            role      = child("role").getValue(String::class.java) ?: UserRole.USER.label,
            isBanned  = child("banInfo").child("isBanned").getValue(Boolean::class.java) ?: false,
            banReason = child("banInfo").child("reason").getValue(String::class.java) ?: "",
            createdAt = child("createdAt").getValue(Long::class.java) ?: 0L
        )
    }.getOrNull()

    private fun DataSnapshot.toBanInfo(): BanInfo = runCatching {
        BanInfo(
            isBanned     = child("isBanned").getValue(Boolean::class.java) ?: false,
            reason       = child("reason").getValue(String::class.java) ?: "",
            bannedBy     = child("bannedBy").getValue(String::class.java) ?: "",
            bannedByName = child("bannedByName").getValue(String::class.java) ?: "",
            bannedAt     = child("bannedAt").getValue(Long::class.java) ?: 0L,
            expiresAt    = child("expiresAt").getValue(Long::class.java) ?: 0L
        )
    }.getOrElse { BanInfo() }

    // =========================================================
    // DOMAIN → FIREBASE MAP
    // =========================================================

    private fun ChatMessage.toFirebaseMap() = mapOf(
        "id"             to id,
        "userId"         to userId,
        "userName"       to userName,
        "userAvatar"     to userAvatar,
        "userRole"       to userRole,
        "content"        to content,
        "timestamp"      to timestamp,
        "replyToId"      to replyToId,
        "replyToContent" to replyToContent,
        "isDeleted"      to isDeleted
    )

    private fun SharedNovel.toFirebaseMap() = mapOf(
        "id"            to id,
        "title"         to title,
        "uploaderName"  to uploaderName,
        "uploaderId"    to uploaderId,
        "fileUrl"       to fileUrl,
        "fileSize"      to fileSize,
        "downloadCount" to downloadCount,
        "uploadedAt"    to uploadedAt
    )

    private fun User.toFirebaseMap() = mapOf(
        "id"        to id,
        "name"      to name,
        "email"     to email,
        "avatarUrl" to avatarUrl,
        "isPremium" to isPremium,
        "role"      to role,
        "createdAt" to createdAt,
        "banInfo"   to mapOf(
            "isBanned"     to false,
            "reason"       to "",
            "bannedBy"     to "",
            "bannedByName" to "",
            "bannedAt"     to 0L,
            "expiresAt"    to 0L
        )
    )
}
