package com.example.novelreader.data.remote.firebase

import com.example.novelreader.domain.model.*
import com.example.novelreader.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================
// FIREBASE USER REPOSITORY
// Handles: registration, login, profile, role management, bans
// ============================================================

@Singleton
class FirebaseUserRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth
) : UserRepository {

    companion object {
        const val USERS_NODE = "users"
    }

    // ─────────────────────────────────────────────────────────
    // Auth helpers
    // ─────────────────────────────────────────────────────────

    override val currentUserId: String? get() = auth.currentUser?.uid
    override val isLoggedIn: Boolean get() = auth.currentUser != null

    /** Read a user's role directly — single lightweight read. */
    private suspend fun roleOf(uid: String): UserRole {
        val snap = usersRef().child(uid).child("role").get().await()
        return UserRole.fromString(snap.getValue(String::class.java))
    }

    private suspend fun requireAtLeast(minimum: UserRole) {
        val uid = currentUserId ?: throw SecurityException("Bạn cần đăng nhập")
        val current = roleOf(uid)
        val ladder = listOf(UserRole.USER, UserRole.MOD, UserRole.ADMIN)
        if (ladder.indexOf(current) < ladder.indexOf(minimum))
            throw SecurityException("Bạn không có quyền thực hiện thao tác này")
    }

    private fun usersRef() = database.reference.child(USERS_NODE)

    // ─────────────────────────────────────────────────────────
    // Auth
    // ─────────────────────────────────────────────────────────

    override suspend fun signUp(name: String, email: String, password: String): Result<User> =
        runCatching {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("Đăng ký thất bại — UID rỗng")
            val user = User(
                id = uid,
                name = name.trim(),
                email = email.trim().lowercase(),
                role = UserRole.USER.label,
                createdAt = System.currentTimeMillis()
            )
            usersRef().child(uid).setValue(user.toFirebaseMap()).await()
            user
        }

    override suspend fun signIn(email: String, password: String): Result<User> = runCatching {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw Exception("Đăng nhập thất bại — UID rỗng")
        val user = fetchUser(uid) ?: throw Exception("Không tìm thấy hồ sơ người dùng")
        if (user.banInfo.isActive)
            throw Exception("Tài khoản bị khóa: ${user.banInfo.reason}")
        user
    }

    override suspend fun signOut() = auth.signOut()

    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    // ─────────────────────────────────────────────────────────
    // Profile
    // ─────────────────────────────────────────────────────────

    override suspend fun fetchCurrentUser(): User? {
        val uid = currentUserId ?: return null
        return fetchUser(uid)
    }

    override fun observeCurrentUser(): Flow<User?> = callbackFlow {
        val uid = currentUserId ?: run { trySend(null); close(); return@callbackFlow }
        val ref = usersRef().child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { trySend(snapshot.toUser()) }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun updateProfile(name: String, avatarUrl: String): Result<Unit> = runCatching {
        val uid = currentUserId ?: throw Exception("Bạn cần đăng nhập")
        require(name.isNotBlank()) { "Tên không được để trống" }
        val updates = mutableMapOf<String, Any>("name" to name.trim())
        if (avatarUrl.isNotBlank()) updates["avatarUrl"] = avatarUrl.trim()
        usersRef().child(uid).updateChildren(updates).await()
    }

    // ─────────────────────────────────────────────────────────
    // Admin / Mod
    // ─────────────────────────────────────────────────────────

    override fun observeAllUsers(): Flow<List<UserSummary>> = callbackFlow {
        val ref = usersRef().orderByChild("createdAt")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children
                    .mapNotNull { it.toUserSummary() }
                    .sortedByDescending { it.createdAt }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun updateRole(targetUid: String, newRole: UserRole): Result<Unit> = runCatching {
        requireAtLeast(UserRole.ADMIN)

        val targetSnap = usersRef().child(targetUid).get().await()
        val targetRole = UserRole.fromString(targetSnap.child("role").getValue(String::class.java))
        if (targetRole == UserRole.ADMIN)
            throw SecurityException("Không thể thay đổi quyền của Admin khác")

        usersRef().child(targetUid).child("role").setValue(newRole.label).await()
    }

    override suspend fun banUser(
        targetUid: String,
        reason: String,
        durationHours: Long
    ): Result<Unit> = runCatching {
        val actorUid = currentUserId ?: throw Exception("Bạn cần đăng nhập")
        requireAtLeast(UserRole.MOD)

        val actorRole = roleOf(actorUid)
        val targetRole = roleOf(targetUid)

        if (targetRole == UserRole.ADMIN)
            throw SecurityException("Không thể khóa tài khoản Admin")
        if (targetRole == UserRole.MOD && actorRole != UserRole.ADMIN)
            throw SecurityException("Chỉ Admin mới có thể khóa tài khoản Mod")
        if (reason.isBlank())
            throw IllegalArgumentException("Lý do khóa không được để trống")

        val actorName = fetchUser(actorUid)?.name ?: "Kiểm duyệt viên"
        val expiresAt = if (durationHours > 0)
            System.currentTimeMillis() + durationHours * 3_600_000L
        else 0L

        val banMap = mapOf(
            "isBanned"     to true,
            "reason"       to reason.trim(),
            "bannedBy"     to actorUid,
            "bannedByName" to actorName,
            "bannedAt"     to System.currentTimeMillis(),
            "expiresAt"    to expiresAt
        )
        usersRef().child(targetUid).child("banInfo").setValue(banMap).await()
    }

    override suspend fun unbanUser(targetUid: String): Result<Unit> = runCatching {
        requireAtLeast(UserRole.MOD)
        val cleanBan = mapOf(
            "isBanned"     to false,
            "reason"       to "",
            "bannedBy"     to "",
            "bannedByName" to "",
            "bannedAt"     to 0L,
            "expiresAt"    to 0L
        )
        usersRef().child(targetUid).child("banInfo").setValue(cleanBan).await()
    }

    // ─────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────

    private suspend fun fetchUser(uid: String): User? =
        usersRef().child(uid).get().await().toUser()

    // ─────────────────────────────────────────────────────────
    // Snapshot mappers
    // ─────────────────────────────────────────────────────────

    private fun DataSnapshot.toUser(): User? = runCatching {
        User(
            id        = child("id").getValue(String::class.java) ?: key ?: return@runCatching null,
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
            id        = child("id").getValue(String::class.java) ?: key ?: return@runCatching null,
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

    // ─────────────────────────────────────────────────────────
    // Domain → Firebase map
    // ─────────────────────────────────────────────────────────

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
