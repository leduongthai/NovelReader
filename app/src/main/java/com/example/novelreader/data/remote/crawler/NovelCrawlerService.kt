package com.example.novelreader.data.remote.crawler

import android.util.Log
import com.example.novelreader.domain.model.Book
import com.example.novelreader.domain.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class CrawlerConfig(
    val baseUrl: String,
    val listSelector: String,
    val titleSelector: String,
    val coverSelector: String,
    val authorSelector: String,
    val descriptionSelector: String,
    val chapterListSelector: String,
    val chapterContentSelector: String,
    val nextPageSelector: String = "",
    val chapterNextPageSelector: String = "",
    val chapterListContainerSelector: String = "",
    val chapterListSkipFirst: Int = 0,
    val searchUrlPattern: String = "",
    val homeUrl: String = "",
    val userAgent: String = "Mozilla/5.0 (Linux; Android 10; Pixel 4) AppleWebKit/537.36"
)

object CrawlerConfigs {
    val TRUYEN_FULL = CrawlerConfig(
        baseUrl = "https://truyenfull.io",
        listSelector = "div.list-truyen .row",
        titleSelector = "h3.truyen-title a",
        coverSelector = "div.book img",
        authorSelector = "span.author",
        descriptionSelector = "div.desc-text",
        chapterListSelector = "ul.list-chapter li a",
        chapterContentSelector = "div#chapter-c p",
        searchUrlPattern = "{base}/?s={query}",
        homeUrl = "https://truyenfull.io"
    )

    val TANG_THU_VIEN = CrawlerConfig(
        baseUrl = "https://truyen.tangthuvien.vn",
        listSelector = "ul.book-img-text li",
        titleSelector = "h4 a",
        coverSelector = "img.lazy",
        authorSelector = "p.author a",
        descriptionSelector = "div.book-intro p",
        chapterListSelector = "ul#danh-sach-chuong li a",
        chapterContentSelector = "div.box-chap p",
        searchUrlPattern = "{base}/tim-kiem?term={query}",
        homeUrl = "https://truyen.tangthuvien.vn"
    )

    val SIX_NINE_HSW = CrawlerConfig(
        baseUrl = "https://www.69hsw.com",
        listSelector = "#newscontent li",
        titleSelector = ".s2 a",
        coverSelector = "#fmimg img",
        authorSelector = ".s4",
        descriptionSelector = "#intro",
        chapterListSelector = "#list a[rel=chapter]",  // ✅ Sửa ở đây
        chapterListSkipFirst = 0,                       // ✅ Không cần skip nữa
        chapterContentSelector = "#booktxt",
        chapterNextPageSelector = "#next_url",
        nextPageSelector = "#pagelink a:last-child",
        searchUrlPattern = "{base}/ss/?searchkey={query}",
        homeUrl = "https://www.69hsw.com/class/5_1.html"
    )
}

@Singleton
class NovelCrawlerService @Inject constructor() {

    companion object {
        private const val TIMEOUT_MS = 15_000
        private const val TAG = "Crawler"
    }

    suspend fun discoverNovels(
        pageUrl: String,
        config: CrawlerConfig
    ): Result<List<Book>> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = fetchDocument(pageUrl, config.userAgent)
            Log.d(TAG, "discoverNovels page_title=${doc.title()} url=$pageUrl")

            val books = mutableListOf<Book>()
            val seenUrls = mutableSetOf<String>()

            val hotItems = doc.select("#hotcontent .item")
            Log.d(TAG, "discoverNovels: hotItems=${hotItems.size}")
            hotItems.forEach { el ->
                val titleEl = el.selectFirst("dt a") ?: return@forEach
                val url = titleEl.attr("abs:href").takeIf { it.isNotBlank() } ?: return@forEach
                if (!seenUrls.add(url)) return@forEach
                val coverEl = el.selectFirst(".image img")
                books.add(Book(
                    id = UUID.nameUUIDFromBytes(url.toByteArray()).toString(),
                    title = titleEl.text().trim(),
                    author = el.selectFirst(".btm a")?.text()?.trim() ?: "不明",
                    coverUrl = coverEl?.attr("abs:data-original")?.ifBlank { null }
                        ?: coverEl?.attr("abs:src") ?: "",
                    description = el.selectFirst("dd")?.text()?.trim() ?: "",
                    source = "crawl",
                    sourceUrl = url
                ))
            }

            val listItems = doc.select(config.listSelector)
            Log.d(TAG, "discoverNovels: listItems=${listItems.size}")
            listItems.forEach { el ->
                val titleEl = el.selectFirst(config.titleSelector) ?: return@forEach
                val url = titleEl.attr("abs:href").takeIf { it.isNotBlank() } ?: return@forEach
                if (!seenUrls.add(url)) return@forEach
                val author = el.selectFirst(".s4")?.text()?.trim()
                    ?: el.selectFirst(".s5")?.text()?.trim() ?: "不明"
                books.add(Book(
                    id = UUID.nameUUIDFromBytes(url.toByteArray()).toString(),
                    title = titleEl.text().trim(),
                    author = author,
                    coverUrl = "",
                    description = "",
                    source = "crawl",
                    sourceUrl = url
                ))
            }

            Log.d(TAG, "discoverNovels: returning ${books.size} books (${hotItems.size} hot + ${listItems.size} list)")
            books
        }.onFailure { Log.e(TAG, "discoverNovels failed: ${it.message}", it) }
    }

    suspend fun searchNovels(
        keyword: String,
        config: CrawlerConfig,
        page: Int = 1
    ): Result<List<Book>> = withContext(Dispatchers.IO) {
        runCatching {
            if (config.searchUrlPattern.isBlank()) return@runCatching emptyList()
            val encoded = java.net.URLEncoder.encode(keyword.trim(), "UTF-8")
            val searchUrl = config.searchUrlPattern
                .replace("{base}", config.baseUrl)
                .replace("{query}", encoded)
            Log.d(TAG, "searchNovels url=$searchUrl")
            val doc = fetchDocument(searchUrl, config.userAgent)

            val results = mutableListOf<Book>()
            val seenUrls = mutableSetOf<String>()

            doc.select("#newscontent li").forEach { el ->
                val titleEl = el.selectFirst(".s2 a") ?: return@forEach
                val url = titleEl.attr("abs:href").takeIf { it.isNotBlank() } ?: return@forEach
                if (!seenUrls.add(url)) return@forEach
                results.add(Book(
                    id = UUID.nameUUIDFromBytes(url.toByteArray()).toString(),
                    title = titleEl.text().trim(),
                    author = el.selectFirst(".s4")?.text()?.trim()
                        ?: el.selectFirst(".s5")?.text()?.trim() ?: "不明",
                    coverUrl = "", description = "", source = "crawl", sourceUrl = url
                ))
            }
            if (results.isEmpty()) {
                doc.select("#hotcontent .item").forEach { el ->
                    val titleEl = el.selectFirst("dt a") ?: return@forEach
                    val url = titleEl.attr("abs:href").takeIf { it.isNotBlank() } ?: return@forEach
                    if (!seenUrls.add(url)) return@forEach
                    val coverEl = el.selectFirst(".image img")
                    results.add(Book(
                        id = UUID.nameUUIDFromBytes(url.toByteArray()).toString(),
                        title = titleEl.text().trim(),
                        author = el.selectFirst(".btm a")?.text()?.trim() ?: "不明",
                        coverUrl = coverEl?.attr("abs:data-original")?.ifBlank { null }
                            ?: coverEl?.attr("abs:src") ?: "",
                        description = "", source = "crawl", sourceUrl = url
                    ))
                }
            }
            Log.d(TAG, "searchNovels: returning ${results.size} results")
            results
        }.onFailure { Log.e(TAG, "searchNovels failed: ${it.message}", it) }
    }

    suspend fun fetchNovelDetail(
        detailUrl: String,
        config: CrawlerConfig
    ): Result<Pair<Book, List<Chapter>>> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = fetchDocument(detailUrl, config.userAgent)
            Log.d(TAG, "fetchNovelDetail url=$detailUrl title=${doc.title()}")

            // DEBUG: dump #list section to find correct chapter selector
            val listEl = doc.selectFirst("#list")
            Log.d(TAG, "fetchNovelDetail_LIST_HTML: ${listEl?.outerHtml()?.take(3000) ?: "NULL - #list not found"}")

            val title = doc.selectFirst("#info h1")?.text()?.trim()
                ?: doc.selectFirst("h1")?.text()?.trim() ?: ""
            val coverEl = doc.selectFirst("#fmimg img") ?: doc.selectFirst(config.coverSelector)
            val cover = coverEl?.attr("abs:data-original")?.ifBlank { null }
                ?: coverEl?.attr("abs:src") ?: ""
            val author = doc.selectFirst("#info p a")?.text()?.trim()
                ?: doc.selectFirst(config.authorSelector)?.text()?.trim() ?: "不明"
            val description = doc.selectFirst(config.descriptionSelector)?.text()?.trim() ?: ""
            val bookId = UUID.nameUUIDFromBytes(detailUrl.toByteArray()).toString()

            val seenUrls = mutableSetOf<String>()
            val chapterLinks = mutableListOf<Pair<String, String>>()
            var currentDoc: Document? = doc
            var isFirstPage = true

            while (currentDoc != null) {
                val links = currentDoc.select(config.chapterListSelector).let { all ->
                    if (isFirstPage && config.chapterListSkipFirst > 0)
                        all.drop(config.chapterListSkipFirst)
                    else all
                }
                Log.d(TAG, "chapterList: page links=${links.size}")
                isFirstPage = false

                links.forEach { link ->
                    val url = link.attr("abs:href")
                    if (url.isNotBlank() && seenUrls.add(url)) {
                        chapterLinks.add(link.text().trim() to url)
                    }
                }

                val nextUrl = config.nextPageSelector.takeIf { it.isNotEmpty() }
                    ?.let { sel ->
                        val href = currentDoc!!.selectFirst(sel)?.attr("abs:href") ?: ""
                        if (href.isBlank() || href.contains("javascript", ignoreCase = true)) null
                        else href
                    }
                currentDoc = nextUrl?.let {
                    runCatching { fetchDocument(it, config.userAgent) }.getOrNull()
                }
            }

            Log.d(TAG, "fetchNovelDetail: title='$title' chapters=${chapterLinks.size}")

            val book = Book(
                id = bookId, title = title, author = author,
                description = description, coverUrl = cover,
                source = "crawl", sourceUrl = detailUrl,
                totalChapters = chapterLinks.size
            )
            val chapters = chapterLinks.mapIndexed { index, (chTitle, chUrl) ->
                Chapter(
                    id = UUID.nameUUIDFromBytes(chUrl.toByteArray()).toString(),
                    bookId = bookId, title = chTitle, content = "",
                    chapterIndex = index, sourceUrl = chUrl, isLoaded = false
                )
            }
            book to chapters
        }.onFailure { Log.e(TAG, "fetchNovelDetail failed: ${it.message}", it) }
    }

    suspend fun fetchChapterContent(
        chapterUrl: String,
        config: CrawlerConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val pages = mutableListOf<String>()
            var currentUrl: String? = chapterUrl
            val visited = mutableSetOf<String>()

            while (currentUrl != null && visited.add(currentUrl)) {
                val doc = fetchDocument(currentUrl, config.userAgent)
                val contentEl = doc.selectFirst(config.chapterContentSelector)
                val pageText = if (contentEl != null) {
                    contentEl.select("script, style").remove()
                    val paragraphs = contentEl.select("p")
                    if (paragraphs.isNotEmpty())
                        paragraphs.joinToString("\n\n") { it.text().trim() }.trim()
                    else contentEl.text().trim()
                } else {
                    doc.body().text()
                }
                if (pageText.isNotBlank()) pages.add(pageText)

                currentUrl = config.chapterNextPageSelector.takeIf { it.isNotEmpty() }
                    ?.let { sel ->
                        val href = doc.selectFirst(sel)?.attr("abs:href") ?: ""
                        if (href.isBlank() || href.contains("javascript", ignoreCase = true)
                            || href == currentUrl) null
                        else href
                    }
            }
            pages.joinToString("\n\n")
        }.onFailure { Log.e(TAG, "fetchChapterContent failed: ${it.message}", it) }
    }

    private fun fetchDocument(url: String, userAgent: String): Document =
        Jsoup.connect(url)
            .userAgent(userAgent)
            .timeout(TIMEOUT_MS)
            .followRedirects(true)
            .ignoreHttpErrors(true)
            .get()
}

// ============================================================
// TXT FILE PARSER
// ============================================================

class TxtChapterParser {
    companion object {
        val DEFAULT_CHAPTER_REGEX = Regex(
            pattern = """^(?:Chương|CHƯƠNG|Chapter|CHAPTER|Phần|Part)\s*\d+.*$""",
            options = setOf(RegexOption.MULTILINE)
        )
        val EXTENDED_REGEX = Regex(
            pattern = """^[ 　\t]{0,4}(?:[Cc]hapter|[Ss]ection|[Pp]art|ＰＡＲＴ|[Nn][Oo].|[Ee]pisode)\s{0,4}\d{1,4}.{0,30}$""",
            options = setOf(RegexOption.MULTILINE)
        )
    }

    fun parse(text: String, bookId: String, regex: Regex = DEFAULT_CHAPTER_REGEX): List<Chapter> {
        val normalized = text.trimStart('\uFEFF').replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split("\n")
        val chapters = mutableListOf<Chapter>()
        var currentTitle = "Giới thiệu"
        val currentContent = StringBuilder()
        var chapterIndex = 0
        var firstHeadingFound = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && regex.matches(trimmed)) {
                if (!firstHeadingFound) {
                    if (currentContent.isNotBlank())
                        chapters.add(buildChapter(bookId, currentTitle, currentContent.toString(), chapterIndex++))
                    firstHeadingFound = true
                } else {
                    chapters.add(buildChapter(bookId, currentTitle, currentContent.toString(), chapterIndex++))
                }
                currentContent.clear()
                currentTitle = trimmed
            } else {
                currentContent.append(line).append('\n')
            }
        }
        if (firstHeadingFound || currentContent.isNotBlank())
            chapters.add(buildChapter(bookId, currentTitle, currentContent.toString(), chapterIndex))
        return chapters
    }

    private fun buildChapter(bookId: String, title: String, content: String, index: Int) = Chapter(
        id = UUID.randomUUID().toString(), bookId = bookId,
        title = title, content = content.trim(),
        chapterIndex = index, isLoaded = true
    )
}