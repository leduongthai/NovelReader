package com.example.novelreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.novelreader.data.local.dao.*
import com.example.novelreader.data.local.entity.*

@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        ReadingHistoryEntity::class,
        PromptEntity::class,
        UserEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun promptDao(): PromptDao
    abstract fun userDao(): UserDao

    companion object {
        const val DATABASE_NAME = "ai_novel_reader.db"
    }
}
