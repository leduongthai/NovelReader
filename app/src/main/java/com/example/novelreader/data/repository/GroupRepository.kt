package com.example.novelreader.data.repository

import android.util.Log
import com.example.novelreader.domain.model.ChatGroup
import com.example.novelreader.domain.model.ChatMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth
) {
    companion object {
        private const val GROUPS_NODE = "community/groups"
        private const val GROUP_MESSAGES_NODE = "community/group_messages"
        private const val MESSAGE_LIMIT = 100
    }

    val currentUserId: String? get() = auth.currentUser?.uid
    val isLoggedIn: Boolean get() = auth.currentUser != null

    // ---- Groups ----

    fun getGroups(): Flow<List<ChatGroup>> = callbackFlow {
        val ref = database.reference.child(GROUPS_NODE).orderByChild("createdAt")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children
                    .mapNotNull { it.toGroup() }
                    .sortedByDescending { it.createdAt }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun createGroup(name: String, description: String): Result<ChatGroup> = runCatching {
        val user = auth.currentUser ?: throw Exception("Bạn cần đăng nhập")
        Log.d("GroupRepo", "createGroup: uid=${user.uid} name=$name")
        val id = UUID.randomUUID().toString()
        Log.d("GroupRepo", "createGroup: writing to RTDB path=community/groups/$id")
        val group = ChatGroup(
            id = id,
            name = name.trim(),
            description = description.trim(),
            adminId = user.uid,
            members = listOf(user.uid),
            createdAt = System.currentTimeMillis()
        )
        database.reference.child(GROUPS_NODE).child(id)
            .setValue(group.toFirebaseMap()).await()
        Log.d("GroupRepo", "createGroup: SUCCESS")
        group
    }

    suspend fun joinGroup(groupId: String): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Bạn cần đăng nhập")
        val ref = database.reference.child(GROUPS_NODE).child(groupId).child("members")
        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                val current = data.children
                    .mapNotNull { it.getValue(String::class.java) }
                    .toMutableList()
                if (uid !in current) current.add(uid)
                data.value = current
                return Transaction.success(data)
            }
            override fun onComplete(
                error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?
            ) {}
        })
    }

    suspend fun leaveGroup(groupId: String): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Bạn cần đăng nhập")
        val ref = database.reference.child(GROUPS_NODE).child(groupId).child("members")
        val snap = ref.get().await()
        val current = snap.children.mapNotNull { it.getValue(String::class.java) }.toMutableList()
        current.remove(uid)
        ref.setValue(current).await()
    }

    suspend fun kickMember(groupId: String, targetUid: String): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Bạn cần đăng nhập")
        val groupSnap = database.reference.child(GROUPS_NODE).child(groupId).get().await()
        val adminId = groupSnap.child("adminId").getValue(String::class.java)
        if (uid != adminId) throw Exception("Chỉ admin mới có quyền này")
        val ref = database.reference.child(GROUPS_NODE).child(groupId).child("members")
        val snap = ref.get().await()
        val current = snap.children.mapNotNull { it.getValue(String::class.java) }.toMutableList()
        current.remove(targetUid)
        ref.setValue(current).await()
    }

    // ---- Messages ----

    fun getMessages(groupId: String): Flow<List<ChatMessage>> = callbackFlow {
        val ref = database.reference
            .child(GROUP_MESSAGES_NODE)
            .child(groupId)
            .orderByChild("timestamp")
            .limitToLast(MESSAGE_LIMIT)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { it.toMessage() }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun sendMessage(
        groupId: String,
        content: String,
        replyToId: String? = null,
        replyToContent: String? = null
    ): Result<Unit> = runCatching {
        val user = auth.currentUser ?: throw Exception("Bạn cần đăng nhập")
        val msgId = UUID.randomUUID().toString()
        val msg = mapOf(
            "id" to msgId,
            "userId" to user.uid,
            "userName" to (user.displayName ?: "Ẩn danh"),
            "userAvatar" to "",
            "content" to content.trim(),
            "timestamp" to System.currentTimeMillis(),
            "replyToId" to replyToId,
            "replyToContent" to replyToContent
        )
        database.reference
            .child(GROUP_MESSAGES_NODE)
            .child(groupId)
            .child(msgId)
            .setValue(msg).await()
    }

    suspend fun deleteMessage(groupId: String, messageId: String): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Bạn cần đăng nhập")
        val groupSnap = database.reference.child(GROUPS_NODE).child(groupId).get().await()
        val adminId = groupSnap.child("adminId").getValue(String::class.java)
        if (uid != adminId) throw Exception("Chỉ admin mới có thể xóa tin nhắn")
        database.reference
            .child(GROUP_MESSAGES_NODE)
            .child(groupId)
            .child(messageId)
            .removeValue().await()
    }

    // ---- Mappers ----

    private fun DataSnapshot.toGroup(): ChatGroup? = runCatching {
        ChatGroup(
            id = child("id").getValue(String::class.java) ?: key ?: "",
            name = child("name").getValue(String::class.java) ?: "",
            description = child("description").getValue(String::class.java) ?: "",
            adminId = child("adminId").getValue(String::class.java) ?: "",
            members = child("members").children
                .mapNotNull { it.getValue(String::class.java) },
            createdAt = child("createdAt").getValue(Long::class.java) ?: 0L
        )
    }.getOrNull()

    private fun DataSnapshot.toMessage(): ChatMessage? = runCatching {
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

    private fun ChatGroup.toFirebaseMap() = mapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "adminId" to adminId,
        "members" to members,
        "createdAt" to createdAt
    )
}