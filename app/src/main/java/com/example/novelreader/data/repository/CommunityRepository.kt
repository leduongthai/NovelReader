package com.example.novelreader.data.repository

import com.example.novelreader.domain.model.ChatMessage
import com.example.novelreader.domain.model.Prompt
import com.example.novelreader.domain.model.SharedNovel
import com.example.novelreader.domain.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================
// FIREBASE COMMUNITY REPOSITORY
// Handles: chat, shared novels, prompt forum, auth
// ============================================================

@Singleton
class CommunityRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val storage: FirebaseStorage,
    val auth: FirebaseAuth
) {

    companion object {
        private const val CHAT_NODE = "chat/messages"
        private const val PROMPTS_NODE = "community/prompts"
        private const val SHARED_NOVELS_NODE = "community/shared_novels"
        private const val USERS_NODE = "users"
        private const val CHAT_MESSAGE_LIMIT = 100   // Load last 100 messages
    }

    // ---- Authentication ----

    val currentUserId: String? get() = auth.currentUser?.uid
    val isLoggedIn: Boolean get() = auth.currentUser != null

    suspend fun signUp(name: String, email: String, password: String): Result<User> =
        runCatching {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("Đăng ký thất bại")
            val user = User(id = uid, name = name, email = email)
            database.reference.child(USERS_NODE).child(uid)
                .setValue(user.toFirebaseMap()).await()
            user
        }

    suspend fun signIn(email: String, password: String): Result<User> = runCatching {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw Exception("Đăng nhập thất bại")
        fetchUser(uid) ?: throw Exception("Không tìm thấy thông tin người dùng")
    }

    suspend fun signOut() = auth.signOut()

    private suspend fun fetchUser(uid: String): User? {
        val snap = database.reference.child(USERS_NODE).child(uid).get().await()
        return snap.toUser()
    }

    // ---- Real-time Chat ----

    /**
     * Returns a Flow that emits the latest [CHAT_MESSAGE_LIMIT] messages.
     * Automatically updates when new messages arrive via Firebase listener.
     */
    fun getChatMessages(): Flow<List<ChatMessage>> = callbackFlow {
        val ref = database.reference
            .child(CHAT_NODE)
            .orderByChild("timestamp")
            .limitToLast(CHAT_MESSAGE_LIMIT)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children.mapNotNull { it.toChatMessage() }
                trySend(messages)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Sends a chat message. Requires user to be logged in.
     */
    suspend fun sendMessage(
        content: String,
        replyToId: String? = null,
        replyToContent: String? = null
    ): Result<Unit> = runCatching {
        val user = auth.currentUser ?: throw Exception("Bạn cần đăng nhập để chat")
        val messageId = UUID.randomUUID().toString()

        // Fetch display name from Firebase profile or users node
        val userData = fetchUser(user.uid)
        val displayName = userData?.name ?: user.displayName ?: "Ẩn danh"

        val message = ChatMessage(
            id = messageId,
            userId = user.uid,
            userName = displayName,
            userAvatar = userData?.avatarUrl ?: "",
            content = content.trim(),
            timestamp = System.currentTimeMillis(),
            replyToId = replyToId,
            replyToContent = replyToContent
        )

        database.reference.child(CHAT_NODE).child(messageId)
            .setValue(message.toFirebaseMap()).await()
    }

    // ---- Prompt Forum ----

    fun getPrompts(): Flow<List<Prompt>> = callbackFlow {
        val ref = database.reference.child(PROMPTS_NODE)
            .orderByChild("likes")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val prompts = snapshot.children.mapNotNull { it.toPrompt() }.sortedByDescending { it.likes }
                trySend(prompts)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun postPrompt(title: String, content: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: throw Exception("Bạn cần đăng nhập để đăng prompt")
        val userData = fetchUser(user.uid)
        val id = UUID.randomUUID().toString()

        val prompt = mapOf(
            "id" to id,
            "title" to title,
            "content" to content,
            "authorId" to user.uid,
            "authorName" to (userData?.name ?: "Ẩn danh"),
            "likes" to 0,
            "createdAt" to System.currentTimeMillis()
        )

        database.reference.child(PROMPTS_NODE).child(id).setValue(prompt).await()
    }

    suspend fun likePrompt(promptId: String): Result<Unit> = runCatching {
        auth.currentUser ?: throw Exception("Bạn cần đăng nhập để thích prompt")
        val ref = database.reference.child(PROMPTS_NODE).child(promptId).child("likes")
        ref.runTransaction(object : com.google.firebase.database.Transaction.Handler {
            override fun doTransaction(
                data: com.google.firebase.database.MutableData
            ): com.google.firebase.database.Transaction.Result {
                val current = data.getValue(Int::class.java) ?: 0
                data.value = current + 1
                return com.google.firebase.database.Transaction.success(data)
            }
            override fun onComplete(
                error: com.google.firebase.database.DatabaseError?,
                committed: Boolean,
                snapshot: com.google.firebase.database.DataSnapshot?
            ) {}
        })
    }

    // ---- Shared Novels ----

    fun getSharedNovels(): Flow<List<SharedNovel>> = callbackFlow {
        val ref = database.reference.child(SHARED_NOVELS_NODE)
            .orderByChild("uploadedAt")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val novels = snapshot.children.mapNotNull { it.toSharedNovel() }
                    .sortedByDescending { it.uploadedAt }
                trySend(novels)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Uploads a TXT file to Firebase Storage and records metadata in Realtime DB.
     */
    suspend fun shareNovelFile(
        title: String,
        fileBytes: ByteArray
    ): Result<SharedNovel> = runCatching {
        val user = auth.currentUser ?: throw Exception("Bạn cần đăng nhập để chia sẻ truyện")
        val userData = fetchUser(user.uid)
        val id = UUID.randomUUID().toString()

        // Upload to Storage
        val storageRef = storage.reference.child("shared_novels/$id.txt")
        storageRef.putBytes(fileBytes).await()
        val downloadUrl = storageRef.downloadUrl.await().toString()

        val novel = SharedNovel(
            id = id,
            title = title,
            uploaderName = userData?.name ?: "Ẩn danh",
            uploaderId = user.uid,
            fileUrl = downloadUrl,
            fileSize = fileBytes.size.toLong(),
            uploadedAt = System.currentTimeMillis()
        )

        database.reference.child(SHARED_NOVELS_NODE).child(id)
            .setValue(novel.toFirebaseMap()).await()

        novel
    }

    // ---- Firebase snapshot mappers ----

    private fun DataSnapshot.toChatMessage(): ChatMessage? = runCatching {
        ChatMessage(
            id = child("id").getValue(String::class.java) ?: key ?: "",
            userId = child("userId").getValue(String::class.java) ?: "",
            userName = child("userName").getValue(String::class.java) ?: "Ẩn danh",
            userAvatar = child("userAvatar").getValue(String::class.java) ?: "",
            content = child("content").getValue(String::class.java) ?: "",
            timestamp = child("timestamp").getValue(Long::class.java) ?: 0L,
            replyToId = child("replyToId").getValue(String::class.java),
            replyToContent = child("replyToContent").getValue(String::class.java)
        )
    }.getOrNull()

    private fun DataSnapshot.toPrompt(): Prompt? = runCatching {
        Prompt(
            id = child("id").getValue(String::class.java) ?: key ?: "",
            title = child("title").getValue(String::class.java) ?: "",
            content = child("content").getValue(String::class.java) ?: "",
            authorId = child("authorId").getValue(String::class.java) ?: "",
            authorName = child("authorName").getValue(String::class.java) ?: "",
            likes = child("likes").getValue(Int::class.java) ?: 0,
            createdAt = child("createdAt").getValue(Long::class.java) ?: 0L
        )
    }.getOrNull()

    private fun DataSnapshot.toSharedNovel(): SharedNovel? = runCatching {
        SharedNovel(
            id = child("id").getValue(String::class.java) ?: key ?: "",
            title = child("title").getValue(String::class.java) ?: "",
            uploaderName = child("uploaderName").getValue(String::class.java) ?: "",
            uploaderId = child("uploaderId").getValue(String::class.java) ?: "",
            fileUrl = child("fileUrl").getValue(String::class.java) ?: "",
            fileSize = child("fileSize").getValue(Long::class.java) ?: 0L,
            downloadCount = child("downloadCount").getValue(Int::class.java) ?: 0,
            uploadedAt = child("uploadedAt").getValue(Long::class.java) ?: 0L
        )
    }.getOrNull()

    private fun DataSnapshot.toUser(): User? = runCatching {
        User(
            id = child("id").getValue(String::class.java) ?: key ?: "",
            name = child("name").getValue(String::class.java) ?: "",
            email = child("email").getValue(String::class.java) ?: "",
            avatarUrl = child("avatarUrl").getValue(String::class.java) ?: "",
            role = child("role").getValue(String::class.java) ?: "user"
        )
    }.getOrNull()

    // ---- Domain -> Firebase Map ----

    private fun ChatMessage.toFirebaseMap() = mapOf(
        "id" to id, "userId" to userId, "userName" to userName,
        "userAvatar" to userAvatar, "content" to content,
        "timestamp" to timestamp, "replyToId" to replyToId,
        "replyToContent" to replyToContent
    )

    private fun SharedNovel.toFirebaseMap() = mapOf(
        "id" to id, "title" to title, "uploaderName" to uploaderName,
        "uploaderId" to uploaderId, "fileUrl" to fileUrl,
        "fileSize" to fileSize, "downloadCount" to downloadCount,
        "uploadedAt" to uploadedAt
    )

    private fun User.toFirebaseMap() = mapOf(
        "id" to id, "name" to name, "email" to email,
        "avatarUrl" to avatarUrl, "isPremium" to isPremium,
        "role" to role
    )
}
