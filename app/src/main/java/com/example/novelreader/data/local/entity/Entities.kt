package com.example.novelreader.data.local.entity

import androidx.room.*

// ============================================================
// ROOM ENTITIES — Maps directly to SQLite tables
// ============================================================

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val description: String,
    val coverUrl: String,
    val source: String,
    val sourceUrl: String,
    val totalChapters: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chapters",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")]
)
data class ChapterEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val title: String,
    val content: String,
    val translatedContent: String = "",
    val chapterIndex: Int,
    val sourceUrl: String = "",
    val isLoaded: Boolean = false
)

@Entity(
    tableName = "reading_history",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")]
)
data class ReadingHistoryEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterId: String,
    val chapterIndex: Int,
    val scrollPosition: Float,
    val lastReadAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "prompts")
data class PromptEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val authorId: String,
    val authorName: String,
    val likes: Int,
    val createdAt: Long
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String,
    val isPremium: Boolean,
    val createdAt: Long
)
