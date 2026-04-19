package com.example.novelreader.data.repository

import com.example.novelreader.domain.model.Review
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth
) {
    companion object {
        private const val REVIEWS_NODE = "community/reviews"
    }

    fun getReviews(bookKey: String): Flow<List<Review>> = callbackFlow {
        val ref = database.reference
            .child(REVIEWS_NODE)
            .child(sanitizeKey(bookKey))
            .orderByChild("createdAt")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children
                    .mapNotNull { it.toReview() }
                    .sortedByDescending { it.createdAt }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun submitReview(
        bookKey: String, bookId: String, sourceUrl: String,
        rating: Int, comment: String
    ): Result<Unit> = runCatching {
        val user = auth.currentUser ?: throw Exception("Bạn cần đăng nhập để đánh giá")
        val review = mapOf(
            "id" to user.uid,
            "bookId" to bookId,
            "sourceUrl" to sourceUrl,
            "userId" to user.uid,
            "userName" to (user.displayName ?: "Ẩn danh"),
            "rating" to rating,
            "comment" to comment.trim(),
            "createdAt" to System.currentTimeMillis()
        )
        database.reference
            .child(REVIEWS_NODE)
            .child(sanitizeKey(bookKey))
            .child(user.uid)
            .setValue(review).await()
    }

    suspend fun getMyReview(bookKey: String): Review? {
        val uid = auth.currentUser?.uid ?: return null
        return runCatching {
            database.reference
                .child(REVIEWS_NODE)
                .child(sanitizeKey(bookKey))
                .child(uid).get().await().toReview()
        }.getOrNull()
    }

    fun sanitizeKey(key: String) = key.replace(Regex("[.#\$\\[\\]/]"), "_")

    private fun DataSnapshot.toReview(): Review? = runCatching {
        Review(
            id = child("id").getValue(String::class.java) ?: key ?: "",
            bookId = child("bookId").getValue(String::class.java) ?: "",
            sourceUrl = child("sourceUrl").getValue(String::class.java) ?: "",
            userId = child("userId").getValue(String::class.java) ?: "",
            userName = child("userName").getValue(String::class.java) ?: "Ẩn danh",
            rating = child("rating").getValue(Int::class.java) ?: 0,
            comment = child("comment").getValue(String::class.java) ?: "",
            createdAt = child("createdAt").getValue(Long::class.java) ?: 0L
        )
    }.getOrNull()
}