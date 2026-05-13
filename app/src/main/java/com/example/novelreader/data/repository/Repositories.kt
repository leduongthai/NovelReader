package com.example.novelreader.data.repository

import android.util.Log
import com.example.novelreader.data.local.dao.*
import com.example.novelreader.data.local.entity.*
import com.example.novelreader.data.remote.api.GeminiTranslationService
import com.example.novelreader.data.remote.crawler.NovelCrawlerService
import com.example.novelreader.data.remote.crawler.TxtChapterParser
import com.example.novelreader.domain.model.*
import com.example.novelreader.domain.repository.BookRepository
import com.example.novelreader.domain.repository.ChapterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================
// BOOK REPOSITORY IMPLEMENTATION
// ============================================================

@Singleton
class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val historyDao: ReadingHistoryDao,
    private val crawler: NovelCrawlerService
) : BookRepository {

    override fun getAllBooks(): Flow<List<Book>> =
        bookDao.getAllBooks().map { list -> list.map { it.toDomain() } }

    override suspend fun getBookById(id: String): Book? =
        bookDao.getBookById(id)?.toDomain()

    override suspend fun getBookBySourceUrl(sourceUrl: String): Book? =
        bookDao.getBookBySourceUrl(sourceUrl)?.toDomain()

    override suspend fun addBook(book: Book) =
        bookDao.insertBook(book.toEntity())

    override suspend fun deleteBook(bookId: String) {
        bookDao.deleteBookById(bookId)
        historyDao.deleteHistoryForBook(bookId)
    }

    override suspend fun updateBookInfo(bookId: String, title: String, author: String) =
        bookDao.updateBookInfo(bookId, title, author)

    override fun getReadingProgress(bookId: String): Flow<ReadingHistory?> =
        historyDao.getAllHistory().map { list ->
            list.firstOrNull { it.bookId == bookId }?.toDomain()
        }

    override suspend fun updateReadingProgress(history: ReadingHistory) =
        historyDao.upsertHistory(history.toEntity())

    /** Crawl trang chi tiết truyện, lưu Book + stub Chapter vào DB */
    override suspend fun crawlAndSaveNovel(detailUrl: String): Result<Book> {
        return crawler.fetchNovelDetail(detailUrl).map { (book, chapters) ->
            val savedBook = book.copy(totalChapters = chapters.size)
            bookDao.insertBook(savedBook.toEntity())
            chapterDao.insertChapters(chapters.map { it.toEntity() })
            savedBook
        }
    }

    /** Import file TXT: parse chương rồi lưu vào DB */
    override suspend fun importTxtFile(
        rawText: String,
        bookTitle: String,
        customRegex: String?
    ): Result<Book> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val bookId = UUID.randomUUID().toString()
            val book = Book(id = bookId, title = bookTitle, source = "local")

            val parser = TxtChapterParser()
            val regex = customRegex?.let { Regex(it, setOf(RegexOption.MULTILINE)) }
                ?: TxtChapterParser.DEFAULT_CHAPTER_REGEX

            val chapters = parser.parse(rawText, bookId, regex)

            bookDao.insertBook(book.copy(totalChapters = chapters.size).toEntity())
            // Chia batch tránh transaction quá lớn với file nhiều chương
            chapters.chunked(200).forEach { batch ->
                chapterDao.insertChapters(batch.map { it.toEntity() })
            }

            book.copy(totalChapters = chapters.size)
        }
    }
}

// ============================================================
// CHAPTER REPOSITORY IMPLEMENTATION
// ============================================================

@Singleton
class ChapterRepositoryImpl @Inject constructor(
    private val chapterDao: ChapterDao,
    private val crawler: NovelCrawlerService,
    private val geminiService: GeminiTranslationService
) : ChapterRepository {

    override fun getChaptersByBook(bookId: String): Flow<List<Chapter>> =
        chapterDao.getChaptersByBook(bookId).map { list -> list.map { it.toDomain() } }

    override suspend fun getChapterById(id: String): Chapter? =
        chapterDao.getChapterById(id)?.toDomain()

    override suspend fun getChapterByIndex(bookId: String, index: Int): Chapter? =
        chapterDao.getChapterByIndex(bookId, index)?.toDomain()

    /**
     * Đảm bảo nội dung chương đã được tải. Nếu chưa có thì crawl từ sourceUrl.
     * NovelCrawlerService tự phát hiện nguồn qua detectSource().
     */
    override suspend fun ensureContentLoaded(chapter: Chapter): Result<Chapter> {
        Log.d("Crawler", "ensureContentLoaded id=${chapter.id} isLoaded=${chapter.isLoaded} sourceUrl=${chapter.sourceUrl}")
        if (chapter.isLoaded) return Result.success(chapter)
        if (chapter.sourceUrl.isBlank()) return Result.failure(Exception("No source URL"))

        return crawler.fetchChapterContent(chapter.sourceUrl).map { content ->
            Log.d("Crawler", "ensureContentLoaded fetched ${content.length} chars")
            chapterDao.updateContent(chapter.id, content)
            chapter.copy(content = content, isLoaded = true)
        }.onFailure {
            Log.e("Crawler", "ensureContentLoaded failed: ${it.message}", it)
        }
    }

    /** Dịch chương bằng Gemini và cache kết quả vào DB */
    override suspend fun translateChapter(
        chapter: Chapter,
        prompt: String,
        apiKey: String
    ): Result<String> {
        return geminiService.translateChapter(chapter.content, prompt, apiKey)
            .onSuccess { translation ->
                chapterDao.updateTranslation(chapter.id, translation)
            }
    }

    override suspend fun toggleBookmark(chapterId: String, bookmarked: Boolean) {
        chapterDao.updateBookmark(chapterId, bookmarked)
    }
}

// ============================================================
// MAPPER EXTENSIONS — Entity <-> Domain
// ============================================================

fun BookEntity.toDomain() = Book(
    id = id, title = title, author = author,
    description = description, coverUrl = coverUrl,
    source = source, sourceUrl = sourceUrl,
    totalChapters = totalChapters, addedAt = addedAt
)

fun Book.toEntity() = BookEntity(
    id = id, title = title, author = author,
    description = description, coverUrl = coverUrl,
    source = source, sourceUrl = sourceUrl,
    totalChapters = totalChapters, addedAt = addedAt
)

fun ChapterEntity.toDomain() = Chapter(
    id = id, bookId = bookId, title = title,
    content = content, translatedContent = translatedContent,
    chapterIndex = chapterIndex, sourceUrl = sourceUrl, isLoaded = isLoaded,
    isBookmarked = isBookmarked
)

fun Chapter.toEntity() = ChapterEntity(
    id = id, bookId = bookId, title = title,
    content = content, translatedContent = translatedContent,
    chapterIndex = chapterIndex, sourceUrl = sourceUrl, isLoaded = isLoaded,
    isBookmarked = isBookmarked
)

fun ReadingHistoryEntity.toDomain() = ReadingHistory(
    id = id, bookId = bookId, chapterId = chapterId,
    chapterIndex = chapterIndex, scrollPosition = scrollPosition, lastReadAt = lastReadAt
)

fun ReadingHistory.toEntity() = ReadingHistoryEntity(
    id = id.ifEmpty { UUID.randomUUID().toString() },
    bookId = bookId, chapterId = chapterId,
    chapterIndex = chapterIndex, scrollPosition = scrollPosition, lastReadAt = lastReadAt
)
