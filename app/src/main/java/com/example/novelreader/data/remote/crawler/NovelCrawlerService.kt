package com.example.novelreader.data.remote.crawler

import android.util.Base64
import android.util.Log
import com.example.novelreader.domain.model.Book
import com.example.novelreader.domain.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class SourceGroup(val title: String) {
    VIETNAMESE("Truyen Viet"),
    CHINESE("Truyen Trung")
}

enum class NovelSource(val label: String, val group: SourceGroup) {
    TRUYENFULL("truyenfull", SourceGroup.VIETNAMESE),
    HAKO("hako", SourceGroup.VIETNAMESE),
    TWKAN("twkan", SourceGroup.CHINESE),
    HSZ_69("69hsz", SourceGroup.CHINESE),
    TRXS("trxs", SourceGroup.CHINESE),
    UNKNOWN("unknown", SourceGroup.CHINESE)
}

enum class DiscoverFeed(
    val title: String,
    val group: SourceGroup,
    val source: NovelSource,
    val input: String,
    val enabled: Boolean = true
) {
    VI_TRUYENFULL_NEW(
        "TruyenFull moi",
        SourceGroup.VIETNAMESE,
        NovelSource.TRUYENFULL,
        "https://truyenfull.today/danh-sach/truyen-moi/"
    ),
    VI_TRUYENFULL_HOT(
        "TruyenFull hot",
        SourceGroup.VIETNAMESE,
        NovelSource.TRUYENFULL,
        "https://truyenfull.today/danh-sach/truyen-hot/"
    ),
    VI_HAKO_UPDATED(
        "Hako cap nhat",
        SourceGroup.VIETNAMESE,
        NovelSource.HAKO,
        "https://docln.sbs/danh-sach?truyendich=1&sangtac=1&convert=1&dangtienhanh=1&tamngung=1&hoanthanh=1&sapxep=capnhat",
        enabled = false
    ),
    VI_HAKO_TOP_MONTH(
        "Hako top thang",
        SourceGroup.VIETNAMESE,
        NovelSource.HAKO,
        "https://docln.sbs/danh-sach?truyendich=1&sangtac=1&convert=1&dangtienhanh=1&tamngung=1&hoanthanh=1&sapxep=topthang",
        enabled = false
    ),
    ZH_TWKAN_ALL(
        "Twkan tat ca",
        SourceGroup.CHINESE,
        NovelSource.TWKAN,
        "/novels/full/0_{0}.html",
        enabled = false
    ),
    ZH_TWKAN_FANTASY(
        "Twkan huyen huyen",
        SourceGroup.CHINESE,
        NovelSource.TWKAN,
        "/novels/full/1_{0}.html",
        enabled = false
    ),
    ZH_69HSZ_HOME(
        "69hsz goi y",
        SourceGroup.CHINESE,
        NovelSource.HSZ_69,
        "https://www.69hsw.com/"
    ),
    ZH_TRXS_TONGREN(
        "TRXS dong nhan",
        SourceGroup.CHINESE,
        NovelSource.TRXS,
        "https://trxs.cc/tongren/index.html"
    )
}

fun detectSource(url: String): NovelSource {
    val lower = url.lowercase()
    return when {
        lower.contains("truyenfull.") -> NovelSource.TRUYENFULL
        lower.contains("docln.") || lower.contains("hako.") || lower.contains("ln.hako.") -> NovelSource.HAKO
        lower.contains("twkan.com") -> NovelSource.TWKAN
        lower.contains("69hsz.") || lower.contains("69hsw.") || lower.contains("69hao.") -> NovelSource.HSZ_69
        lower.contains("trxs.") -> NovelSource.TRXS
        else -> NovelSource.UNKNOWN
    }
}

@Singleton
class NovelCrawlerService @Inject constructor() {
    companion object {
        private const val TAG = "Crawler"
        private const val TIMEOUT_S = 25L
        private const val TRUYENFULL_HOST = "https://truyenfull.today"
        private const val HAKO_HOST = "https://docln.sbs"
        private const val TWKAN_HOST = "https://twkan.com"
        private const val TRXS_HOST = "https://trxs.cc"

        private const val HSZ69_HOST = "https://www.69hsw.com"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    suspend fun discoverNovels(
        page: Int = 1,
        feed: DiscoverFeed = DiscoverFeed.VI_TRUYENFULL_NEW
    ): Result<List<Book>> = withContext(Dispatchers.IO) {
        runCatching {
            when (feed.source) {
                NovelSource.TRUYENFULL -> discoverTruyenFull(feed.input, page)
                NovelSource.HAKO -> discoverHako(feed.input, page)
                NovelSource.TWKAN -> discoverTwkan(feed.input, page)
                NovelSource.HSZ_69 -> discover69hsz(feed.input, page)
                NovelSource.TRXS -> discoverTrxs(page)
                NovelSource.UNKNOWN -> emptyList()
            }
        }.onFailure { Log.e(TAG, "discoverNovels failed: ${it.message}", it) }
    }

    suspend fun searchNovels(
        keyword: String,
        page: Int = 1,
        feed: DiscoverFeed = DiscoverFeed.VI_TRUYENFULL_NEW
    ): Result<List<Book>> = withContext(Dispatchers.IO) {
        runCatching {
            when (feed.source) {
                NovelSource.TRUYENFULL -> searchTruyenFull(keyword, page)
                NovelSource.HAKO -> searchHako(keyword, page)
                NovelSource.TWKAN -> searchTwkan(keyword)
                NovelSource.HSZ_69 -> search69hsz(keyword)
                NovelSource.TRXS -> searchTrxs(keyword, page)
                NovelSource.UNKNOWN -> emptyList()
            }
        }.onFailure { Log.e(TAG, "searchNovels failed: ${it.message}", it) }
    }

    suspend fun fetchNovelDetail(detailUrl: String): Result<Pair<Book, List<Chapter>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (detectSource(detailUrl)) {
                    NovelSource.TRUYENFULL -> fetchDetailTruyenFull(detailUrl)
                    NovelSource.HAKO -> fetchDetailHako(detailUrl)
                    NovelSource.TWKAN -> fetchDetailTwkan(detailUrl)
                    NovelSource.HSZ_69 -> fetchDetail69hsz(detailUrl)
                    NovelSource.TRXS -> fetchDetailTrxs(detailUrl)
                    NovelSource.UNKNOWN -> throw IOException("Nguon truyen chua duoc ho tro: $detailUrl")
                }
            }.onFailure { Log.e(TAG, "fetchNovelDetail failed: ${it.message}", it) }
        }

    suspend fun fetchChapterContent(chapterUrl: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (detectSource(chapterUrl)) {
                    NovelSource.TRUYENFULL -> fetchChapTruyenFull(chapterUrl)
                    NovelSource.HAKO -> fetchChapHako(chapterUrl)
                    NovelSource.TWKAN -> fetchChapTwkan(chapterUrl)
                    NovelSource.HSZ_69 -> fetchChap69hsz(chapterUrl)
                    NovelSource.TRXS -> fetchChapTrxs(chapterUrl)
                    NovelSource.UNKNOWN -> throw IOException("Nguon chuong chua duoc ho tro: $chapterUrl")
                }
            }.onFailure { Log.e(TAG, "fetchChapterContent failed: ${it.message}", it) }
        }

    // ---------------------------------------------------------------------
    // TruyenFull.today
    // ---------------------------------------------------------------------

    private fun discoverTruyenFull(input: String, page: Int): List<Book> {
        val url = "${input.trimEnd('/')}/trang-$page"
        return parseTruyenFullList(fetchHtml(url))
    }

    private fun searchTruyenFull(keyword: String, page: Int): List<Book> {
        val encoded = URLEncoder.encode(keyword.trim(), "UTF-8")
        val url = "$TRUYENFULL_HOST/tim-kiem/?tukhoa=$encoded&page=$page"
        return parseTruyenFullList(fetchHtml(url))
    }

    private fun parseTruyenFullList(doc: Document): List<Book> =
        doc.select(".list-truyen div[itemscope]").mapNotNull { item ->
            val link = item.selectFirst(".truyen-title > a")?.absHref("href").orEmpty()
            val title = item.selectFirst(".truyen-title > a")?.text()?.trim().orEmpty()
            if (link.isBlank() || title.isBlank()) return@mapNotNull null
            val cover = item.selectFirst("[data-image]")?.attr("data-image")
                ?.toAbsoluteUrl(TRUYENFULL_HOST).orEmpty()
            Book(
                id = uuidFrom(link),
                title = title,
                author = item.select(".author").text().trim(),
                description = item.select(".text-info, .author").text().trim(),
                coverUrl = cover,
                source = "crawl:${NovelSource.TRUYENFULL.label}",
                sourceUrl = link
            )
        }

    private fun fetchDetailTruyenFull(detailUrl: String): Pair<Book, List<Chapter>> {
        val url = normalizeHost(detailUrl, TRUYENFULL_HOST)
        val doc = fetchHtml(url)
        val bookId = uuidFrom(url)
        val description = doc.selectFirst("div.desc-text")?.html()?.let(::htmlToReaderText).orEmpty()
        val chapters = fetchTocTruyenFull(doc, bookId)
        val book = Book(
            id = bookId,
            title = doc.selectFirst("h3.title")?.text()?.trim().orEmpty(),
            author = doc.selectFirst("a[itemprop=author]")?.text()?.trim().orEmpty(),
            description = description,
            coverUrl = doc.selectFirst("div.book img")?.absHref("src").orEmpty(),
            source = "crawl:${NovelSource.TRUYENFULL.label}",
            sourceUrl = url,
            totalChapters = chapters.size
        )
        return book to chapters
    }

    private fun fetchTocTruyenFull(doc: Document, bookId: String): List<Chapter> {
        val storyId = doc.select("#truyen-id").attr("value")
        val storyAscii = doc.select("#truyen-ascii").attr("value")
        val totalPage = doc.select("#total-page").attr("value").toIntOrNull() ?: 1
        val chapters = mutableListOf<Chapter>()

        if (storyId.isNotBlank() && storyAscii.isNotBlank()) {
            for (page in 1..totalPage.coerceAtLeast(1)) {
                val url = "$TRUYENFULL_HOST/ajax.php?type=list_chapter&tid=$storyId&tascii=$storyAscii&page=$page&totalp=$totalPage"
                val chapListHtml = JSONObject(rawGet(url)).optString("chap_list")
                Jsoup.parse(chapListHtml, TRUYENFULL_HOST)
                    .select(".list-chapter li a")
                    .forEach { a ->
                        val href = a.absHref("href").ifBlank { a.attr("href").toAbsoluteUrl(TRUYENFULL_HOST) }
                        chapters.add(chapterStub(bookId, a.text().trim(), href, chapters.size))
                    }
            }
        }

        if (chapters.isEmpty()) {
            doc.select(".list-chapter li a, #list-chapter li a").forEach { a ->
                val href = a.absHref("href").ifBlank { a.attr("href").toAbsoluteUrl(TRUYENFULL_HOST) }
                chapters.add(chapterStub(bookId, a.text().trim(), href, chapters.size))
            }
        }

        return chapters.distinctBy { it.sourceUrl }.mapIndexed { index, chapter ->
            chapter.copy(chapterIndex = index)
        }
    }

    private fun fetchChapTruyenFull(url: String): String {
        val doc = fetchHtml(normalizeHost(url, TRUYENFULL_HOST))
        val content = doc.selectFirst("div.chapter-c") ?: return ""
        content.select("script, style, noscript, iframe, a, div.ads-responsive, em").remove()
        return htmlToReaderText(content.html())
    }

    // ---------------------------------------------------------------------
    // Hako / docln.sbs
    // ---------------------------------------------------------------------

    private fun discoverHako(input: String, page: Int): List<Book> {
        val separator = if (input.contains("?")) "&" else "?"
        val url = "${normalizeHost(input, HAKO_HOST)}${separator}page=$page"
        return parseHakoList(fetchHtml(url))
    }

    private fun searchHako(keyword: String, page: Int): List<Book> {
        val encoded = URLEncoder.encode(keyword.trim(), "UTF-8")
        return parseHakoList(fetchHtml("$HAKO_HOST/tim-kiem?keywords=$encoded&page=$page"))
    }

    private fun parseHakoList(doc: Document): List<Book> =
        doc.select(".thumb-section-flow .thumb-item-flow, .sect-body .thumb-item-flow").mapNotNull { item ->
            val link = item.selectFirst(".series-title a")?.absHref("href").orEmpty()
            val title = item.selectFirst(".series-title a")?.text()?.trim().orEmpty()
            if (link.isBlank() || title.isBlank()) return@mapNotNull null
            Book(
                id = uuidFrom(link),
                title = title,
                author = "",
                description = item.selectFirst(".chapter-title")?.text()?.trim().orEmpty(),
                coverUrl = item.selectFirst(".img-in-ratio")?.attr("data-bg")?.toAbsoluteUrl(HAKO_HOST).orEmpty(),
                source = "crawl:${NovelSource.HAKO.label}",
                sourceUrl = normalizeHost(link, HAKO_HOST)
            )
        }

    private fun fetchDetailHako(detailUrl: String): Pair<Book, List<Chapter>> {
        val url = normalizeHost(detailUrl, HAKO_HOST)
        val doc = fetchHtml(url)
        val bookId = uuidFrom(url)
        val cover = doc.selectFirst(".series-cover .img-in-ratio")
            ?.let { parseCssUrl(it.attr("style")) ?: it.attr("data-bg") }
            ?.toAbsoluteUrl(HAKO_HOST).orEmpty()
        val author = doc.select(".series-information .info-item").firstOrNull {
            val label = it.select(".info-name").text()
            label.contains("Tac", ignoreCase = true) || label.contains("Tác", ignoreCase = true)
        }?.select(".info-value")?.text()?.trim().orEmpty()
        val chapters = doc.select(".volume-list").flatMap { section ->
            val volume = section.selectFirst(".sect-title")?.text()?.trim().orEmpty()
            section.select(".list-chapters li").mapNotNull { item ->
                val a = item.selectFirst(".chapter-name a") ?: return@mapNotNull null
                val name = a.text().trim().ifBlank { "Chuong" }
                val displayName = if (volume.isNotBlank() && item.elementSiblingIndex() == 0) "$volume $name" else name
                chapterStub(bookId, displayName, a.absHref("href").ifBlank { a.attr("href").toAbsoluteUrl(HAKO_HOST) }, 0)
            }
        }.mapIndexed { index, chapter -> chapter.copy(chapterIndex = index) }
        val book = Book(
            id = bookId,
            title = doc.selectFirst(".series-name")?.text()?.trim().orEmpty(),
            author = author,
            description = doc.selectFirst(".summary-content")?.html()?.let(::htmlToReaderText).orEmpty(),
            coverUrl = cover,
            source = "crawl:${NovelSource.HAKO.label}",
            sourceUrl = url,
            totalChapters = chapters.size
        )
        return book to chapters
    }

    private fun fetchChapHako(chapterUrl: String): String {
        val doc = fetchHtml(normalizeHost(chapterUrl, HAKO_HOST))
        val container = doc.selectFirst("#chapter-content") ?: return ""
        val protectedContent = decodeHakoProtectedContent(container)
        container.select("script, style, iframe, p.none, .note-reg, img[src*=/images/banners/], img[src*=/lightnovel/banners/]").remove()
        val raw = protectedContent ?: container.html()
        return htmlToReaderText(
            raw.replace(Regex("""<div id="chapter-c-protected"[\s\S]*?</div>"""), "")
                .replace(Regex("""\[note\d+]"""), "")
        )
    }

    private fun decodeHakoProtectedContent(container: Element): String? {
        val protectedEl = container.selectFirst("#chapter-c-protected") ?: return null
        val mode = protectedEl.attr("data-s").ifBlank { "none" }
        val key = protectedEl.attr("data-k")
        val chunks = JSONArray(protectedEl.attr("data-c").ifBlank { "[]" })
        if (chunks.length() == 0) return null

        val ordered = (0 until chunks.length())
            .map { chunks.getString(it) }
            .sortedBy { it.take(4).toIntOrNull() ?: 0 }

        return ordered.joinToString("") { chunk ->
            val part = chunk.drop(4)
            when (mode) {
                "xor_shuffle" -> xorDecodeBase64(part, key)
                "base64_reverse" -> decodeBase64ToUtf8(part.reversed())
                else -> decodeBase64ToUtf8(part)
            }
        }
    }

    private fun xorDecodeBase64(value: String, key: String): String {
        if (key.isBlank()) return decodeBase64ToUtf8(value)
        val bytes = Base64.decode(value, Base64.DEFAULT)
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val decoded = ByteArray(bytes.size) { i ->
            (bytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
        return String(decoded, Charsets.UTF_8)
    }

    private fun decodeBase64ToUtf8(value: String): String =
        String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)

    // ---------------------------------------------------------------------
    // Twkan
    // ---------------------------------------------------------------------

    private fun discoverTwkan(input: String, page: Int): List<Book> {
        val path = input.replace("{0}", page.toString())
        return parseTwkanList(fetchHtml(TWKAN_HOST + path))
    }

    private fun searchTwkan(keyword: String): List<Book> {
        val doc = Jsoup.connect("$TWKAN_HOST/modules/article/search.php")
            .userAgent(UA_DESKTOP)
            .timeout(TIMEOUT_S.toInt() * 1000)
            .data("searchkey", keyword.trim())
            .data("searchtype", "all")
            .post()
        ensureReadable(doc.html(), "$TWKAN_HOST/modules/article/search.php")
        return if (doc.select("div.booknav2 > h1 > a").isNotEmpty()) {
            val link = doc.selectFirst("div.booknav2 > h1 > a")?.absHref("href").orEmpty()
            listOf(
                Book(
                    id = uuidFrom(link),
                    title = doc.selectFirst("div.booknav2 > h1 > a")?.text()?.trim().orEmpty(),
                    author = doc.selectFirst("div.booknav2 > p:nth-child(2) > a")?.text()?.trim().orEmpty(),
                    coverUrl = doc.selectFirst("div.bookimg2 img")?.absHref("src").orEmpty(),
                    source = "crawl:${NovelSource.TWKAN.label}",
                    sourceUrl = link.ifBlank { TWKAN_HOST }
                )
            )
        } else {
            parseTwkanList(doc)
        }
    }

    private fun parseTwkanList(doc: Document): List<Book> =
        doc.select("#article_list_content li, .newbox li").mapNotNull { item ->
            val a = item.selectFirst(".newnav h3 > a:not([class]), h3 > a") ?: return@mapNotNull null
            val link = a.absHref("href").ifBlank { a.attr("href").toAbsoluteUrl(TWKAN_HOST) }
            val title = a.text().trim()
            if (title.isBlank()) return@mapNotNull null
            Book(
                id = uuidFrom(link),
                title = title,
                author = "",
                description = item.selectFirst(".zxzj > p")?.text()?.trim().orEmpty(),
                coverUrl = item.selectFirst(".imgbox > img")?.attr("data-src")?.toAbsoluteUrl(TWKAN_HOST).orEmpty(),
                source = "crawl:${NovelSource.TWKAN.label}",
                sourceUrl = link
            )
        }

    private fun fetchDetailTwkan(detailUrl: String): Pair<Book, List<Chapter>> {
        val url = normalizeHost(detailUrl, TWKAN_HOST).replace("/txt/", "/book/")
        val doc = fetchHtml(url)
        val bookId = uuidFrom(url)
        val remoteId = Regex("""/(\d+)\.html""").find(url)?.groupValues?.get(1).orEmpty()
        val chapters = if (remoteId.isBlank()) emptyList() else {
            fetchHtml("$TWKAN_HOST/ajax_novels/chapterlist/$remoteId.html")
                .select("ul > li > a")
                .mapIndexed { index, a ->
                    chapterStub(bookId, a.text().trim(), a.attr("href").toAbsoluteUrl(TWKAN_HOST), index)
                }
        }
        val book = Book(
            id = bookId,
            title = doc.selectFirst("div.booknav2 > h1 > a")?.text()?.trim().orEmpty(),
            author = doc.selectFirst("div.booknav2 > p:nth-child(2) > a")?.text()?.trim().orEmpty(),
            description = doc.selectFirst("div.navtxt > p")?.html()?.let(::htmlToReaderText).orEmpty(),
            coverUrl = doc.selectFirst("div.bookimg2 > img")?.absHref("src").orEmpty(),
            source = "crawl:${NovelSource.TWKAN.label}",
            sourceUrl = url,
            totalChapters = chapters.size
        )
        return book to chapters
    }

    private fun fetchChapTwkan(chapterUrl: String): String {
        val doc = fetchHtml(normalizeHost(chapterUrl, TWKAN_HOST))
        val content = doc.selectFirst("#txtcontent0") ?: return ""
        content.select("h1, div, script, style").remove()
        return htmlToReaderText(content.html())
    }

    // ---------------------------------------------------------------------
    // 69hsz / 69hsw
    // ---------------------------------------------------------------------

    private fun discover69hsz(input: String, page: Int): List<Book> {
        val url = when {
            input.contains("{0}") -> input.replace("{0}", page.toString())
            page <= 1 -> input
            else -> "$HSZ69_HOST/class/1_$page.html"
        }
        return parse69hszList(fetchHtml(normalize69hszUrl(url)))
    }

    private fun search69hsz(keyword: String): List<Book> {
        val encoded = URLEncoder.encode(keyword.trim(), "UTF-8")
        val doc = fetchHtml("$HSZ69_HOST/ss/?searchkey=$encoded")
        if (doc.select(".modal-code, input[name=verifycode]").isNotEmpty()) {
            throw IOException("69hsz yeu cau captcha khi tim kiem. Hay chon tu danh sach hoac mo URL chi tiet.")
        }
        return parse69hszList(doc)
    }

    private fun parse69hszList(doc: Document): List<Book> {
        val books = linkedMapOf<String, Book>()

        fun addBook(link: String, title: String, author: String, description: String, coverUrl: String) {
            val normalized = normalize69hszUrl(link)
            if (!is69hszBookUrl(normalized) || title.isBlank() || books.containsKey(normalized)) return
            books[normalized] = Book(
                id = uuidFrom(normalized),
                title = title,
                author = author,
                description = description,
                coverUrl = coverUrl,
                source = "crawl:${NovelSource.HSZ_69.label}",
                sourceUrl = normalized
            )
        }

        doc.select("#hotcontent .item, .l.rank .item").forEach { item ->
            val a = item.selectFirst("dt a") ?: return@forEach
            val cover = item.selectFirst(".image img")
                ?.let { it.attr("data-original").ifBlank { it.attr("src") } }
                ?.toAbsoluteUrl(HSZ69_HOST).orEmpty()
            addBook(
                link = a.absHref("href").ifBlank { a.attr("href").toAbsoluteUrl(HSZ69_HOST) },
                title = a.text().trim(),
                author = item.selectFirst(".btm a")?.text()?.trim()
                    ?: item.selectFirst(".btm")?.ownText()?.trim().orEmpty(),
                description = item.selectFirst("dd")?.text()?.trim().orEmpty(),
                coverUrl = cover
            )
        }

        doc.select("#newscontent li, .novelslist li").forEach { item ->
            val a = item.selectFirst(".s2 a, > a") ?: return@forEach
            addBook(
                link = a.absHref("href").ifBlank { a.attr("href").toAbsoluteUrl(HSZ69_HOST) },
                title = a.text().trim(),
                author = item.selectFirst(".s5, i")?.text()?.trim().orEmpty(),
                description = item.selectFirst(".s3")?.text()?.trim().orEmpty(),
                coverUrl = ""
            )
        }

        return books.values.toList()
    }

    private fun fetchDetail69hsz(detailUrl: String): Pair<Book, List<Chapter>> {
        val url = normalize69hszUrl(detailUrl)
        val doc = fetchHtml(url)
        val bookId = uuidFrom(url)
        val chapters = parse69hszToc(doc, bookId)
        val cover = doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("#fmimg img")
                ?.let { it.attr("data-original").ifBlank { it.attr("src") } }
                ?.toAbsoluteUrl(HSZ69_HOST).orEmpty()
        val book = Book(
            id = bookId,
            title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
                ?: doc.selectFirst("#info h1, h1")?.text()?.trim().orEmpty(),
            author = parse69hszAuthor(doc),
            description = doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
                ?: doc.selectFirst("#intro")?.html()?.let(::htmlToReaderText).orEmpty(),
            coverUrl = cover,
            source = "crawl:${NovelSource.HSZ_69.label}",
            sourceUrl = url,
            totalChapters = chapters.size
        )
        return book to chapters
    }

    private fun parse69hszToc(doc: Document, bookId: String): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        val dl = doc.selectFirst("#list dl")
        if (dl != null) {
            var inFullToc = false
            var titleSectionCount = 0
            dl.children().forEach { child ->
                when {
                    child.tagName().equals("dt", ignoreCase = true) -> {
                        titleSectionCount += 1
                        inFullToc = titleSectionCount >= 2
                    }
                    inFullToc && child.tagName().equals("a", ignoreCase = true) -> {
                        val href = child.attr("href").toAbsoluteUrl(HSZ69_HOST)
                        val title = child.selectFirst("dd")?.text()?.trim().orEmpty()
                        chapters.add(chapterStub(bookId, cleanChineseChapterName(title), href, chapters.size))
                    }
                }
            }
        }

        if (chapters.isEmpty()) {
            doc.select("#list a[rel=chapter]").forEach { a ->
                val title = a.selectFirst("dd")?.text()?.trim().orEmpty().ifBlank { a.text().trim() }
                chapters.add(
                    chapterStub(
                        bookId,
                        cleanChineseChapterName(title),
                        a.attr("href").toAbsoluteUrl(HSZ69_HOST),
                        chapters.size
                    )
                )
            }
        }

        return chapters.distinctBy { it.sourceUrl }.mapIndexed { index, chapter ->
            chapter.copy(chapterIndex = index)
        }
    }

    private fun fetchChap69hsz(chapterUrl: String): String {
        val chunks = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        var nextPage = normalize69hszUrl(chapterUrl)

        while (nextPage.isNotBlank() && visited.add(nextPage) && visited.size <= 8) {
            val doc = fetchHtml(nextPage)
            val content = doc.selectFirst("#booktxt, #content") ?: break
            content.select("script, style, iframe, .readtj, .bottem1, .bottem2").remove()
            val text = htmlToReaderText(content.html())
                .replace("\u672c\u7ae0\u672a\u5b8c\uff0c\u70b9\u51fb\u4e0b\u4e00\u9875\u7ee7\u7eed\u9605\u8bfb\u3002", "")
                .trim()
            if (text.isNotBlank()) chunks.add(text)

            val nextLink = doc.selectFirst("#next_url, a[rel=next]")
            val isNextPage = nextLink?.text()?.contains("\u4e0b\u4e00\u9875") == true
            nextPage = if (isNextPage) {
                nextLink.attr("href").toAbsoluteUrl(HSZ69_HOST)
            } else {
                ""
            }
        }

        return chunks.joinToString("\n\n")
    }

    // ---------------------------------------------------------------------
    // TRXS
    // ---------------------------------------------------------------------

    private fun discoverTrxs(page: Int): List<Book> {
        val path = if (page <= 1) "/tongren/index.html" else "/tongren/index_$page.html"
        return parseTrxsList(fetchGb2312(TRXS_HOST + path))
    }

    private fun searchTrxs(keyword: String, page: Int): List<Book> =
        discoverTrxs(page).filter { it.title.contains(keyword.trim(), ignoreCase = true) }

    private fun parseTrxsList(doc: Document): List<Book> =
        doc.select("div.bk > a").mapNotNull { a ->
            val title = a.selectFirst("div.infos > h3")?.text()?.trim().orEmpty()
            val link = a.attr("href").toAbsoluteUrl(TRXS_HOST)
            if (title.isBlank() || link.isBlank()) return@mapNotNull null
            Book(
                id = uuidFrom(link),
                title = title,
                author = "",
                description = "",
                coverUrl = a.selectFirst("div.pic > img")?.attr("src")?.toAbsoluteUrl(TRXS_HOST).orEmpty(),
                source = "crawl:${NovelSource.TRXS.label}",
                sourceUrl = link
            )
        }

    private fun fetchDetailTrxs(detailUrl: String): Pair<Book, List<Chapter>> {
        val url = normalizeHost(detailUrl, TRXS_HOST)
            .replace("m.trxs.cc", "trxs.cc")
            .replace("www.trxs.cc", "trxs.cc")
        val doc = fetchGb2312(url)
        val bookId = uuidFrom(url)
        val chapters = doc.select("div.book_list.clearfix > ul > li a").mapIndexed { index, a ->
            chapterStub(bookId, a.text().trim(), a.attr("href").toAbsoluteUrl(TRXS_HOST), index)
        }
        val book = Book(
            id = bookId,
            title = doc.selectFirst("div.infos > h1")?.text()?.trim().orEmpty(),
            author = doc.selectFirst("div.book_info.clearfix div.infos div.date span a")?.text()?.trim().orEmpty(),
            description = doc.selectFirst("div.booktips + p")?.text()?.trim().orEmpty(),
            coverUrl = doc.selectFirst("div.book_info.clearfix div.pic img")?.attr("src")?.toAbsoluteUrl(TRXS_HOST).orEmpty(),
            source = "crawl:${NovelSource.TRXS.label}",
            sourceUrl = url,
            totalChapters = chapters.size
        )
        return book to chapters
    }

    private fun fetchChapTrxs(chapterUrl: String): String {
        val doc = fetchGb2312(normalizeHost(chapterUrl, TRXS_HOST))
        return htmlToReaderText(doc.selectFirst("div.read_chapterDetail")?.html().orEmpty())
    }

    // ---------------------------------------------------------------------
    // HTTP and parsing helpers
    // ---------------------------------------------------------------------

    private val UA_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private fun fetchHtml(url: String): Document {
        val html = rawGet(url)
        return Jsoup.parse(html, url)
    }

    private fun fetchGbk(url: String): Document {
        val bytes = rawGetBytes(url)
        val html = String(bytes, charset("GBK"))
        ensureReadable(html, url)
        return Jsoup.parse(html, url)
    }

    private fun fetchGb2312(url: String): Document {
        val bytes = rawGetBytes(url)
        val html = String(bytes, charset("GB2312"))
        ensureReadable(html, url)
        return Jsoup.parse(html, url)
    }

    private fun rawGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA_DESKTOP)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7,zh-CN;q=0.6")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} khi tai $url")
            val body = response.body?.string().orEmpty()
            ensureReadable(body, url)
            body
        }
    }

    private fun rawGetBytes(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA_DESKTOP)
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} khi tai $url")
            response.body?.bytes() ?: ByteArray(0)
        }
    }

    private fun ensureReadable(html: String, url: String) {
        val lower = html.lowercase()
        if (
            lower.contains("just a moment") ||
            lower.contains("cf-chl") ||
            lower.contains("cloudflare") && lower.contains("challenge")
        ) {
            throw IOException("Nguon dang bi Cloudflare chan tu dong: $url")
        }
    }

    private fun chapterStub(bookId: String, title: String, url: String, index: Int): Chapter =
        Chapter(
            id = uuidFrom(url),
            bookId = bookId,
            title = title.ifBlank { "Chuong ${index + 1}" },
            chapterIndex = index,
            sourceUrl = url,
            isLoaded = false
        )

    private fun uuidFrom(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()

    private fun htmlToReaderText(html: String): String {
        val withBreaks = html
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n\n")
            .replace(Regex("(?i)</div>"), "\n\n")
            .replace("&nbsp;", " ")
        return Jsoup.parseBodyFragment(withBreaks)
            .body()
            .wholeText()
            .replace('\u00a0', ' ')
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n[ \\t]+"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun parse69hszAuthor(doc: Document): String {
        val metaAuthor = doc.selectFirst("meta[property=og:novel:author]")?.attr("content")?.trim()
        if (!metaAuthor.isNullOrBlank()) return metaAuthor
        return doc.select("#info p").firstOrNull { it.text().contains("\u4f5c\u8005") }
            ?.text()
            ?.replace(Regex("""^.*[:\uff1a]\s*"""), "")
            ?.trim()
            .orEmpty()
    }

    private fun normalize69hszUrl(url: String): String {
        val absolute = url.toAbsoluteUrl(HSZ69_HOST)
        return absolute.replace(Regex("""^https?://(?:www\.|m\.)?(?:69hsz|69hsw|69hao)\.(?:com|net)"""), HSZ69_HOST)
    }

    private fun is69hszBookUrl(url: String): Boolean =
        Regex("""^https?://[^/]+/\d+/?$""").matches(url)

    private fun normalizeHost(url: String, host: String): String =
        if (url.startsWith("http", ignoreCase = true)) {
            url.replace(Regex("""^https?://(?:www\.)?[^/]+"""), host)
        } else {
            url.toAbsoluteUrl(host)
        }

    private fun String.toAbsoluteUrl(host: String): String = when {
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true) -> this
        startsWith("//") -> "https:$this"
        startsWith("/") -> host.trimEnd('/') + this
        isBlank() -> ""
        else -> host.trimEnd('/') + "/" + this
    }

    private fun Element.absHref(attr: String): String = absUrl(attr)

    private fun String.hostRoot(): String =
        Regex("""^(https?://[^/]+)""").find(this)?.groupValues?.get(1).orEmpty()

    private fun parseCssUrl(style: String): String? =
        Regex("""url\(['"]?(.*?)['"]?\)""").find(style)?.groupValues?.get(1)

    private fun extractFirstNumber(value: String): String? =
        Regex("""(\d+)""").find(value)?.groupValues?.get(1)

    private fun cleanChineseChapterName(name: String): String =
        name.replace(Regex("""^\d+\.第(\d+)章"""), "第$1章")
}

class TxtChapterParser {
    companion object {
        private const val FALLBACK_CHUNK_SIZE = 24_000
        private const val MAX_SINGLE_CHAPTER_CHARS = 60_000

        val DEFAULT_CHAPTER_REGEX = Regex(
            pattern = """^(?:Chương|Chuong|CHƯƠNG|CHUONG|Chapter|CHAPTER|Phần|Phan|Part)\s*\d+.*$""",
            options = setOf(RegexOption.MULTILINE)
        )
    }

    fun parse(text: String, bookId: String, regex: Regex = DEFAULT_CHAPTER_REGEX): List<Chapter> {
        val normalized = text.trimStart('\uFEFF').replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split("\n")
        val chapters = mutableListOf<Chapter>()
        var currentTitle = "Gioi thieu"
        val currentContent = StringBuilder()
        var chapterIndex = 0
        var firstHeadingFound = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && regex.matches(trimmed)) {
                if (!firstHeadingFound) {
                    if (currentContent.isNotBlank()) {
                        chapters.add(build(bookId, currentTitle, currentContent.toString(), chapterIndex++))
                    }
                    firstHeadingFound = true
                } else {
                    chapters.add(build(bookId, currentTitle, currentContent.toString(), chapterIndex++))
                }
                currentContent.clear()
                currentTitle = trimmed
            } else {
                currentContent.append(line).append('\n')
            }
        }
        if (firstHeadingFound || currentContent.isNotBlank()) {
            chapters.add(build(bookId, currentTitle, currentContent.toString(), chapterIndex))
        }

        if (!firstHeadingFound) return splitFallback(bookId, normalized, "Phan")

        if (chapters.size == 1 && chapters.first().content.length > MAX_SINGLE_CHAPTER_CHARS) {
            val only = chapters.first()
            return splitFallback(bookId, only.content, only.title)
        }

        return chapters
    }

    private fun splitFallback(bookId: String, content: String, titlePrefix: String): List<Chapter> {
        val normalized = content.trim()
        if (normalized.isBlank()) return emptyList()

        val parts = mutableListOf<String>()
        val chunk = StringBuilder()
        val blocks = normalized
            .split(Regex("""\n\s*\n+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        fun flushChunk() {
            if (chunk.isNotBlank()) {
                parts.add(chunk.toString().trim())
                chunk.clear()
            }
        }

        for (block in blocks.ifEmpty { listOf(normalized) }) {
            if (block.length > FALLBACK_CHUNK_SIZE) {
                flushChunk()
                block.chunked(FALLBACK_CHUNK_SIZE).forEach { parts.add(it.trim()) }
            } else {
                if (chunk.length + block.length + 2 > FALLBACK_CHUNK_SIZE) flushChunk()
                if (chunk.isNotEmpty()) chunk.append("\n\n")
                chunk.append(block)
            }
        }
        flushChunk()

        return parts.mapIndexed { index, part ->
            build(bookId, "$titlePrefix ${index + 1}", part, index)
        }
    }

    private fun build(bookId: String, title: String, content: String, index: Int) = Chapter(
        id = UUID.randomUUID().toString(),
        bookId = bookId,
        title = title,
        content = content.trim(),
        chapterIndex = index,
        isLoaded = true
    )
}
