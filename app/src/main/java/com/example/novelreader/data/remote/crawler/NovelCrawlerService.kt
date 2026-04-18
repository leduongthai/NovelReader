package com.example.novelreader.data.remote.crawler

import com.example.novelreader.domain.model.Book
import com.example.novelreader.domain.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================
// WEB CRAWLER SERVICE
// Supports multiple novel sites via configurable selectors.
// Add a new CrawlerConfig to support any new website.
// ============================================================

/**
 * CSS selector configuration for a specific novel website.
 * Override only the selectors that differ from defaults.
 */
data class CrawlerConfig(
    val baseUrl: String,
    val listSelector: String,           // Selector for novel list items on listing page
    val titleSelector: String,
    val coverSelector: String,
    val authorSelector: String,
    val descriptionSelector: String,
    val chapterListSelector: String,    // Selector for <a> chapter links on detail page
    val chapterContentSelector: String, // Selector for paragraph content inside chapter
    val nextPageSelector: String = "",  // Paginated chapter list (optional)
    val userAgent: String = "Mozilla/5.0 (Linux; Android 10; Pixel 4) AppleWebKit/537.36"
)

// ---- Default configs for popular Vietnamese novel sites ----
object CrawlerConfigs {
    val TRUYEN_FULL = CrawlerConfig(
        baseUrl = "https://truyenfull.io",
        listSelector = "div.list-truyen .row",
        titleSelector = "h3.truyen-title a",
        coverSelector = "div.book img",
        authorSelector = "span.author",
        descriptionSelector = "div.desc-text",
        chapterListSelector = "ul.list-chapter li a",
        chapterContentSelector = "div#chapter-c p"
    )

    val TANG_THU_VIEN = CrawlerConfig(
        baseUrl = "https://truyen.tangthuvien.vn",
        listSelector = "ul.book-img-text li",
        titleSelector = "h4 a",
        coverSelector = "img.lazy",
        authorSelector = "p.author a",
        descriptionSelector = "div.book-intro p",
        chapterListSelector = "ul#danh-sach-chuong li a",
        chapterContentSelector = "div.box-chap p"
    )

    val SIX_NINE_HSW = CrawlerConfig(
        baseUrl = "https://www.69hsw.com",
        // Selector cho danh sách truyện (ví dụ ở trang Top hoặc Category)
        listSelector = "div.grid div.item",
        titleSelector = "dt a",
        coverSelector = "div.image img",
        authorSelector = "dt span",
        descriptionSelector = "div.book-intro", // Trong trang chi tiết
        chapterListSelector = "div.catalog ul li a", // Danh sách chương
        chapterContentSelector = "div.content", // Nội dung chương
        nextPageSelector = "" // Trang này thường liệt kê hết chương ở 1 trang
    )

}

@Singleton
class NovelCrawlerService @Inject constructor() {

    companion object {
        private const val TIMEOUT_MS = 15_000
        private const val MAX_RETRIES = 3
    }

    /**
     * Fetches a list of novels from the discovery page.
     * Returns a list of partially-filled Book objects (no chapters yet).
     */
    suspend fun discoverNovels(
        pageUrl: String,
        config: CrawlerConfig
    ): Result<List<Book>> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = fetchDocument(pageUrl, config.userAgent)
            doc.select(config.listSelector).mapNotNull { element ->
                runCatching {
                    val titleEl = element.selectFirst(config.titleSelector)
                    val coverEl = element.selectFirst(config.coverSelector)
                    val authorEl = element.selectFirst(config.authorSelector)
                    val detailUrl = titleEl?.attr("abs:href") ?: return@runCatching null

                    Book(
                        id = UUID.nameUUIDFromBytes(detailUrl.toByteArray()).toString(),
                        title = titleEl.text().trim(),
                        author = authorEl?.text()?.trim() ?: "Không rõ",
                        coverUrl = coverEl?.attr("abs:src") ?: coverEl?.attr("abs:data-src") ?: "",
                        description = "",
                        source = "crawl",
                        sourceUrl = detailUrl
                    )
                }.getOrNull()
            }
        }
    }

    /**
     * Fetches full novel details including chapter list.
     * Use the sourceUrl stored in Book to navigate to detail page.
     */
    suspend fun fetchNovelDetail(
        detailUrl: String,
        config: CrawlerConfig
    ): Result<Pair<Book, List<Chapter>>> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = fetchDocument(detailUrl, config.userAgent)

            val title = doc.selectFirst(config.titleSelector)?.text()?.trim() ?: ""
            val cover = doc.selectFirst(config.coverSelector)
                ?.let { it.attr("abs:src").ifEmpty { it.attr("abs:data-src") } } ?: ""
            val author = doc.selectFirst(config.authorSelector)?.text()?.trim() ?: "Không rõ"
            val description = doc.selectFirst(config.descriptionSelector)?.text()?.trim() ?: ""
            val bookId = UUID.nameUUIDFromBytes(detailUrl.toByteArray()).toString()

            // Collect chapters — may span multiple pages
            val chapterLinks = mutableListOf<Pair<String, String>>() // (title, url)
            var currentDoc: Document? = doc

            while (currentDoc != null) {
                currentDoc.select(config.chapterListSelector).forEach { link ->
                    chapterLinks.add(link.text().trim() to link.attr("abs:href"))
                }
                // Follow next-page link if available
                val nextUrl = config.nextPageSelector.takeIf { it.isNotEmpty() }
                    ?.let { currentDoc!!.selectFirst(it)?.attr("abs:href") }
                currentDoc = nextUrl?.let { runCatching { fetchDocument(it, config.userAgent) }.getOrNull() }
            }

            val book = Book(
                id = bookId,
                title = title,
                author = author,
                description = description,
                coverUrl = cover,
                source = "crawl",
                sourceUrl = detailUrl,
                totalChapters = chapterLinks.size
            )

            // Create stub chapters — content will be loaded on demand
            val chapters = chapterLinks.mapIndexed { index, (chTitle, chUrl) ->
                Chapter(
                    id = UUID.nameUUIDFromBytes(chUrl.toByteArray()).toString(),
                    bookId = bookId,
                    title = chTitle,
                    content = "",       // Lazy — loaded when user opens chapter
                    chapterIndex = index,
                    sourceUrl = chUrl,
                    isLoaded = false
                )
            }

            book to chapters
        }
    }

    /**
     * Fetches the actual text content of a single chapter.
     * Called on-demand when reader opens a chapter.
     */
    suspend fun fetchChapterContent(
        chapterUrl: String,
        config: CrawlerConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = fetchDocument(chapterUrl, config.userAgent)
            val paragraphs = doc.select(config.chapterContentSelector)

            if (paragraphs.isEmpty()) {
                // Fallback: grab all text from body
                doc.body().text()
            } else {
                paragraphs.joinToString("\n\n") { it.text().trim() }
                    .trim()
            }
        }
    }

    // ---- Internal helpers ----

    private fun fetchDocument(url: String, userAgent: String): Document {
        return Jsoup.connect(url)
            .userAgent(userAgent)
            .timeout(TIMEOUT_MS)
            .followRedirects(true)
            .ignoreHttpErrors(true)
            .get()
    }
}

// ============================================================
// TXT FILE PARSER — Splits a raw TXT into chapters
// ============================================================

class TxtChapterParser {

    companion object {
        /**
         * Default regex covers Vietnamese/Chinese/English chapter headings.
         * Examples matched:
         *   "Chương 1: Khởi đầu"
         *   "CHAPTER 10 The Beginning"
         *   "Phần 3"
         *   "Part II"
         */
        val DEFAULT_CHAPTER_REGEX = Regex(
            pattern = """^(?:Chương|CHƯƠNG|Chapter|CHAPTER|Phần|Part)\s*\d+.*$""",
            options = setOf(RegexOption.MULTILINE)
        )

        /**
         * Extended regex for full-width / mixed scripts.
         */
        val EXTENDED_REGEX = Regex(
            pattern = """^[ 　\t]{0,4}(?:[Cc]hapter|[Ss]ection|[Pp]art|ＰＡＲＴ|[Nn][Oo].|[Ee]pisode)\s{0,4}\d{1,4}.{0,30}$""",
            options = setOf(RegexOption.MULTILINE)
        )
    }

    /**
     * Parse a raw TXT string into a list of (chapterTitle, content) pairs.
     *
     * @param text      Full raw text of the novel
     * @param bookId    ID of the owning book
     * @param regex     Custom regex to detect chapter boundaries
     */
    fun parse(
        text: String,
        bookId: String,
        regex: Regex = DEFAULT_CHAPTER_REGEX
    ): List<Chapter> {
        val lines = text.lines()
        val chapters = mutableListOf<Chapter>()

        var currentTitle = "Giới thiệu"
        val currentContent = StringBuilder()
        var chapterIndex = 0

        for (line in lines) {
            if (regex.matches(line.trim())) {
                // Flush previous chapter
                if (currentContent.isNotBlank()) {
                    chapters.add(buildChapter(bookId, currentTitle, currentContent.toString(), chapterIndex++))
                    currentContent.clear()
                }
                currentTitle = line.trim()
            } else {
                currentContent.appendLine(line)
            }
        }

        // Flush final chapter
        if (currentContent.isNotBlank()) {
            chapters.add(buildChapter(bookId, currentTitle, currentContent.toString(), chapterIndex))
        }

        return chapters
    }

    private fun buildChapter(bookId: String, title: String, content: String, index: Int): Chapter {
        return Chapter(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            title = title,
            content = content.trim(),
            chapterIndex = index,
            isLoaded = true  // TXT chapters are fully loaded
        )
    }
}
