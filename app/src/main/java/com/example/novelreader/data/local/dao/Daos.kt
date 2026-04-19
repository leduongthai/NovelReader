package com.example.novelreader.data.local.dao

import androidx.room.*
import com.example.novelreader.data.local.entity.*
import kotlinx.coroutines.flow.Flow

// ============================================================
// DATA ACCESS OBJECTS
// ============================================================

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBookById(bookId: String)

    @Query("SELECT COUNT(*) FROM books")
    suspend fun getBookCount(): Int
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex ASC")
    fun getChaptersByBook(bookId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex ASC")
    suspend fun getChaptersByBookSync(bookId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getChapterById(id: String): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND chapterIndex = :index")
    suspend fun getChapterByIndex(bookId: String, index: Int): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    // Update only translated content — avoids overwriting original
    @Query("UPDATE chapters SET translatedContent = :translation WHERE id = :chapterId")
    suspend fun updateTranslation(chapterId: String, translation: String)

    // Mark chapter content as loaded (for crawled chapters)
    @Query("UPDATE chapters SET content = :content, isLoaded = 1 WHERE id = :chapterId")
    suspend fun updateContent(chapterId: String, content: String)

    @Query("SELECT COUNT(*) FROM chapters WHERE bookId = :bookId")
    suspend fun getChapterCount(bookId: String): Int

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteChaptersByBook(bookId: String)

    @Query("UPDATE chapters SET isBookmarked = :bookmarked WHERE id = :chapterId")
    suspend fun updateBookmark(chapterId: String, bookmarked: Boolean)

    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND isBookmarked = 1")
    fun getBookmarkedChapters(bookId: String): Flow<List<ChapterEntity>>

}

@Dao
interface ReadingHistoryDao {
    @Query("SELECT * FROM reading_history WHERE bookId = :bookId LIMIT 1")
    suspend fun getHistoryForBook(bookId: String): ReadingHistoryEntity?

    @Query("SELECT * FROM reading_history ORDER BY lastReadAt DESC")
    fun getAllHistory(): Flow<List<ReadingHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(history: ReadingHistoryEntity)

    @Query("DELETE FROM reading_history WHERE bookId = :bookId")
    suspend fun deleteHistoryForBook(bookId: String)
}

@Dao
interface PromptDao {
    @Query("SELECT * FROM prompts ORDER BY likes DESC")
    fun getAllPrompts(): Flow<List<PromptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompt(prompt: PromptEntity)

    @Query("DELETE FROM prompts WHERE id = :id")
    suspend fun deletePrompt(id: String)

    @Query("UPDATE prompts SET likes = likes + 1 WHERE id = :id")
    suspend fun incrementLikes(id: String)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("DELETE FROM users")
    suspend fun clearUsers()
}
