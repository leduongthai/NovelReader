package com.example.novelreader.domain.repository

import com.example.novelreader.data.remote.crawler.CrawlerConfig
import com.example.novelreader.domain.model.*
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    suspend fun getBookById(id: String): Book?
    suspend fun addBook(book: Book)
    suspend fun deleteBook(bookId: String)
    fun getReadingProgress(bookId: String): Flow<ReadingHistory?>
    suspend fun updateReadingProgress(history: ReadingHistory)
    suspend fun crawlAndSaveNovel(detailUrl: String, config: CrawlerConfig): Result<Book>
    suspend fun importTxtFile(rawText: String, bookTitle: String, customRegex: String?): Result<Book>
}

interface ChapterRepository {
    fun getChaptersByBook(bookId: String): Flow<List<Chapter>>
    suspend fun getChapterById(id: String): Chapter?
    suspend fun getChapterByIndex(bookId: String, index: Int): Chapter?
    suspend fun ensureContentLoaded(chapter: Chapter, config: CrawlerConfig?): Result<Chapter>
    suspend fun translateChapter(chapter: Chapter, prompt: String, apiKey: String): Result<String>
}
