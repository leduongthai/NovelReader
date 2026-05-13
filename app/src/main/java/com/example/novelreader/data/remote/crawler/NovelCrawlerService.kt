package com.example.novelreader.data.remote.crawler

import android.util.Base64
import android.util.Log
import com.example.novelreader.domain.model.Book
import com.example.novelreader.domain.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Dns
import java.net.InetAddress
import java.io.IOException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HttpsURLConnection

// ============================================================
// NGUỒN TRUYỆN — khớp với plugin VBook "5in1"
// ============================================================

enum class NovelSource(val label: String) {
    STV("sangtacviet"),       // Proxy chính: sangtacviet.app
    QIDIAN("qidian"),
    SHU_69("69shu"),
    PTWXZ("ptwxz"),
    FANQIE("fanqie"),
    QIMAO("qimao"),
    UNKNOWN("unknown")
}

enum class DiscoverFeed(val title: String, val host: String, val sort: String) {
    QIDIAN_WEEK("Qidian tuần", "qidian", "viewweek"),
    QIDIAN_DAY("Qidian ngày", "qidian", "viewday"),
    FANQIE_WEEK("Fanqie tuần", "fanqie", "viewweek")
}

// Phát hiện nguồn từ URL (giống regexp trong plugin.json)
fun detectSource(url: String): NovelSource = when {
    url.contains("qidian.com")                          -> NovelSource.QIDIAN
    url.contains("69shu") || url.contains("69shuba")   -> NovelSource.SHU_69
    url.contains("piaotia") || url.contains("ptwxz") ||
            url.contains("piaotian")                    -> NovelSource.PTWXZ
    url.contains("fanqie")                              -> NovelSource.FANQIE
    url.contains("qimao") || url.contains("api-bc.wtzw") ||
            url.contains("api-ks.wtzw")                 -> NovelSource.QIMAO
    url.contains("sangtac") || url.contains("14.225.254.182") -> NovelSource.STV
    else                                                -> NovelSource.UNKNOWN
}

// ============================================================
// CRAWLER SERVICE
// ============================================================

@Singleton
class NovelCrawlerService @Inject constructor() {

    companion object {
        private const val TAG = "Crawler"
        private const val TIMEOUT_S = 20L

        // Host proxy chính (sangtacviet.app)
        private const val STV_HOST = "https://sangtacviet.app"
        private const val HOST_69 = "https://69shuba.com"
        private const val HOST_PTWXZ = "https://www.piaotia.com"
        private const val QIMAO_SECRET = "d3dGiJc651gSQ8w1"
        private const val QIMAO_APP_VERSION = "71900"
        private const val QIMAO_APPLICATION_ID = "com.kmxs.reader"
    }

    // Thêm vào phần HTTP HELPERS cuối file
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return if (hostname == "sangtacviet.app") {
                    // Ép DNS cho sangtacviet.app về IP máy chủ trực tiếp
                    listOf(InetAddress.getByName("14.225.254.182"))
                } else {
                    Dns.SYSTEM.lookup(hostname)
                }
            }
        })
        .hostnameVerifier { hostname, session ->
            // Chỉ nới kiểm tra cho STV/IP; các host khác vẫn dùng verifier mặc định.
            hostname == "sangtacviet.app" ||
                    hostname == "14.225.254.182" ||
                    HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
        }
        .build()

    private fun fetchHtmlViaOkHttp(url: String): Document {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA_DESKTOP)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Lỗi kết nối: ${response.code}")
            val body = response.body?.string() ?: ""
            return Jsoup.parse(body, url)
        }
    }

    // ----------------------------------------------------------
    // TRANG CHỦ — danh sách truyện hot (Qidian qua STV proxy)
    // ----------------------------------------------------------

    suspend fun discoverNovels(page: Int = 1, feed: DiscoverFeed = DiscoverFeed.QIDIAN_WEEK): Result<List<Book>> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Dùng STV để lấy bảng xếp hạng/search proxy giống home.js + gen1.js
                val url = "$STV_HOST/?find=&host=${feed.host}&minc=0&sort=${feed.sort}&step=1&tag=&p=$page"
                val doc = fetchHtml(url)
                parseSTVBookList(doc)
            }.onFailure { Log.e(TAG, "discoverNovels failed: ${it.message}", it) }
        }

    // ----------------------------------------------------------
    // TÌM KIẾM — search qua STV (giống search.js)
    // ----------------------------------------------------------

    suspend fun searchNovels(keyword: String, page: Int = 1): Result<List<Book>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(keyword.trim(), "UTF-8")
                val url = "$STV_HOST/?find=&findinname=$encoded&minc=0&tag=&p=$page"
                val doc = fetchHtml(url)
                parseSTVBookList(doc)
            }.onFailure { Log.e(TAG, "searchNovels failed: ${it.message}", it) }
        }

    // ----------------------------------------------------------
    // CHI TIẾT TRUYỆN + MỤC LỤC (detail.js + toc.js)
    // ----------------------------------------------------------

    suspend fun fetchNovelDetail(detailUrl: String): Result<Pair<Book, List<Chapter>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val source = detectSource(detailUrl)
                Log.d(TAG, "fetchNovelDetail source=$source url=$detailUrl")

                when (source) {
                    NovelSource.QIDIAN -> fetchDetailQidian(detailUrl)
                    NovelSource.SHU_69 -> fetchDetail69shu(detailUrl)
                    NovelSource.PTWXZ  -> fetchDetailPtwxz(detailUrl)
                    NovelSource.QIMAO  -> fetchDetailQimao(detailUrl)
                    else               -> fetchDetailSTV(normalizeStvUrl(detailUrl))
                }
            }.onFailure { Log.e(TAG, "fetchNovelDetail failed: ${it.message}", it) }
        }

    // ----------------------------------------------------------
    // NỘI DUNG CHƯƠNG (chap.js)
    // ----------------------------------------------------------

    suspend fun fetchChapterContent(chapterUrl: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val source = detectSource(chapterUrl)
                Log.d(TAG, "fetchChapterContent source=$source url=$chapterUrl")

                when (source) {
                    NovelSource.QIDIAN -> fetchChapQidian(chapterUrl)
                    NovelSource.SHU_69 -> fetchChap69shu(chapterUrl)
                    NovelSource.PTWXZ  -> fetchChapPtwxz(chapterUrl)
                    NovelSource.FANQIE -> fetchChapFanqie(chapterUrl)
                    NovelSource.QIMAO  -> fetchChapQimao(chapterUrl)
                    else               -> fetchChapSTV(chapterUrl)
                }
            }.onFailure { Log.e(TAG, "fetchChapterContent failed: ${it.message}", it) }
        }

    // ==========================================================
    // PRIVATE — STV proxy (sangtacviet.app)
    // ==========================================================

    private fun normalizeStvUrl(url: String): String {
        // Thay host bất kỳ bằng STV_HOST (giống plugin: url.replace(hostRegex, STVHOST))
        val normalized = url.replace(
            Regex("""^(?:https?://)?(?:[^@\n]+@)?(?:www\.)?([^:/\n?]+)"""),
            STV_HOST
        )
        return if (normalized.endsWith("/")) normalized else "$normalized/"
    }

    /** Parse danh sách truyện từ trang STV (search / ranking) */
    private fun parseSTVBookList(doc: Document): List<Book> {
        val books = mutableListOf<Book>()
        doc.select("#searchviewdiv a.booksearch").forEach { el ->
            val name = el.selectFirst(".searchbooktitle")?.text()?.trim()
                ?.capitalizeWords() ?: return@forEach
            val link = el.attr("href").ifBlank { return@forEach }
            val cover = el.selectFirst("img")?.attr("src") ?: ""
            val description = el.select("div > span.searchtag").lastOrNull()?.text() ?: ""
            val fullLink = if (link.startsWith("http")) link
                          else "$STV_HOST/${link.trimStart('/')}"
            books.add(
                Book(
                    id          = UUID.nameUUIDFromBytes(fullLink.toByteArray()).toString(),
                    title       = name,
                    author      = "",
                    description = description,
                    coverUrl    = cover,
                    source      = "crawl",
                    sourceUrl   = fullLink
                )
            )
        }
        return books
    }

    /** Chi tiết truyện qua STV proxy — giống getDetailStv() */
    private fun fetchDetailSTV(url: String): Pair<Book, List<Chapter>> {
        val doc = fetchHtml(url)

        val title = doc.selectFirst("#oriname")?.text()?.trim() ?: ""
        val author = doc.selectFirst("i.cap")?.attr("onclick")
            ?.replace(Regex("""location='\/\?find=&findinname=(.*?)'"""), "$1")
            ?.trim() ?: "Không rõ"
        val description = doc.selectFirst(".blk:has(.fa-water) .blk-body")?.html() ?: ""
        val cover = doc.selectFirst("meta[property=\"og:image\"]")?.attr("content")
            ?.replace("/cdn/images/nc.jpg", "https://static.sangtacvietcdn.xyz/img/bookcover256.jpg")
            ?: ""

        val bookId = UUID.nameUUIDFromBytes(url.toByteArray()).toString()

        // Xác định nguồn để lấy TOC đúng
        val chapters = when {
            url.contains("qidian") -> {
                val id = extractSTVBookId(url)
                fetchTocQidianById(id, bookId)
            }
            url.contains("69shu") -> {
                val id = extractSTVBookId(url)
                fetchToc69shuById(id, bookId)
            }
            url.contains("ptwxz") -> {
                val id = extractSTVBookId(url)
                fetchTocPtwxzById(id, bookId)
            }
            url.contains("fanqie") -> {
                val id = extractSTVBookId(url)
                fetchTocFanqieById(id, url, bookId)
            }
            url.contains("qimao") -> {
                val id = extractSTVBookId(url)
                fetchTocQimaoById(id, bookId)
            }
            else -> fetchTocFromSTVPage(url, bookId)
        }

        val book = Book(
            id            = bookId,
            title         = title,
            author        = author,
            description   = description,
            coverUrl      = cover,
            source        = "crawl",
            sourceUrl     = url,
            totalChapters = chapters.size
        )
        return book to chapters
    }

    /** Lấy bookId từ URL kiểu /truyen/qidian/1/12345/ */
    private fun extractSTVBookId(url: String): String {
        return Regex("""/1/(\d+)/?""").find(url)?.groupValues?.get(1) ?: ""
    }

    /** TOC từ trang chi tiết STV (không phân biệt nguồn — fallback) */
    private fun fetchTocFromSTVPage(url: String, bookId: String): List<Chapter> {
        val doc = fetchHtml(url)
        val links = doc.select("a[href*='/chuong-']")
        return links.mapIndexed { i, el ->
            val href = el.attr("abs:href")
            Chapter(
                id           = UUID.nameUUIDFromBytes(href.toByteArray()).toString(),
                bookId       = bookId,
                title        = el.text().trim(),
                chapterIndex = i,
                sourceUrl    = href,
                isLoaded     = false
            )
        }
    }

    /** Nội dung chương qua STV (fallback) */
    private fun fetchChapSTV(url: String): String {
        val doc = fetchHtml(url)
        val content = doc.selectFirst("#bookContent, .chapter-content, #content")
        return content?.text()?.trim() ?: ""
    }

    // ==========================================================
    // PRIVATE — Qidian (1qidian.js)
    // ==========================================================

    private fun fetchDetailQidian(url: String): Pair<Book, List<Chapter>> {
        // Chuẩn hóa URL về STV proxy (giống detail.js)
        val bookIdRaw = url.filter { it.isDigit() }.take(10)
        val stvUrl = "$STV_HOST/truyen/qidian/1/$bookIdRaw/"
        return fetchDetailSTV(stvUrl)
    }

    private fun fetchTocQidianById(bookId: String, localBookId: String): List<Chapter> {
        if (bookId.isBlank()) return emptyList()
        return try {
            // m.qidian.com/book/{id}/catalog/ — giống getTocQidian()
            val url = "https://m.qidian.com/book/$bookId/catalog/"
            val doc = fetchHtml(url, userAgent = UA_ANDROID)
            val scriptJson = doc.selectFirst("#vite-plugin-ssr_pageContext")?.html()
                ?.replace(Regex("</?script(.*?)\"?>"), "") ?: return emptyList()
            val json = org.json.JSONObject(scriptJson)
            val vs = json.getJSONObject("pageContext")
                .getJSONObject("pageProps")
                .getJSONObject("pageData")
                .getJSONArray("vs")

            val chapters = mutableListOf<Chapter>()
            var index = 0
            for (v in 0 until vs.length()) {
                val cs = vs.getJSONObject(v).getJSONArray("cs")
                for (c in 0 until cs.length()) {
                    val chap = cs.getJSONObject(c)
                    val chapId = chap.getString("id")
                    val chapUrl = "https://m.qidian.com/chapter/$bookId/$chapId/"
                    chapters.add(
                        Chapter(
                            id           = UUID.nameUUIDFromBytes(chapUrl.toByteArray()).toString(),
                            bookId       = localBookId,
                            title        = chap.getString("cN"),
                            chapterIndex = index++,
                            sourceUrl    = chapUrl,
                            isLoaded     = false
                        )
                    )
                }
            }
            chapters
        } catch (e: Exception) {
            Log.e(TAG, "fetchTocQidianById failed: ${e.message}", e)
            emptyList()
        }
    }

    /** Nội dung chương Qidian mobile — giống getChapQidian() */
    private fun fetchChapQidian(url: String): String {
        val doc = fetchHtml(url, userAgent = UA_ANDROID)
        val content = doc.selectFirst(".content") ?: return ""
        return content.html()
            .replace(Regex("<br\\s*/?>|\\n"), "\n\n")
            .trim()
    }

    // ==========================================================
    // PRIVATE — 69shu / 69shuba (169shu.js)
    // ==========================================================

    private fun fetchDetail69shu(url: String): Pair<Book, List<Chapter>> {
        // Thử parse book ID từ URL trực tiếp
        val idMatch = Regex("""/book/(\d+)/?|/(\d+)\.htm""").find(url)
        val bookId69 = idMatch?.groupValues?.firstOrNull { it.isNotBlank() && it != idMatch.value }
            ?: url.filter { it.isDigit() }

        // Lấy thông tin từ 69shuba.com (giống getDetail69shu)
        val detailUrl = "$HOST_69/book/$bookId69/"
        val doc = fetchGbk(detailUrl)

        val title = doc.selectFirst("div.booknav2 > h1 > a")?.text()?.trim() ?: ""
        val author = doc.selectFirst("div.booknav2 > p:nth-child(3) a")?.text()?.trim() ?: "Không rõ"
        val cover = "https://static.69shuba.com/files/article/image/${bookId69.toLongOrNull()?.div(1000) ?: 0}/$bookId69/${bookId69}s.jpg"
        val description = doc.selectFirst("#jianjie-popup > div > div.content p")?.html() ?: ""
        val localBookId = UUID.nameUUIDFromBytes(url.toByteArray()).toString()

        val chapters = fetchToc69shuById(bookId69, localBookId)
        val book = Book(
            id            = localBookId,
            title         = title,
            author        = author,
            description   = description,
            coverUrl      = cover,
            source        = "crawl",
            sourceUrl     = url,
            totalChapters = chapters.size
        )
        return book to chapters
    }

    private fun fetchToc69shuById(bookId: String, localBookId: String): List<Chapter> {
        if (bookId.isBlank()) return emptyList()
        return try {
            val url = "$HOST_69/book/$bookId/"
            val doc = fetchGbk(url)
            val links = doc.select("div.catalog > ul > li > a:not(#bookcase)")
            links.reversed().mapIndexed { i, el ->
                val href = el.attr("href").let {
                    if (it.startsWith("http")) it else "$HOST_69/${it.trimStart('/')}"
                }
                Chapter(
                    id           = UUID.nameUUIDFromBytes(href.toByteArray()).toString(),
                    bookId       = localBookId,
                    title        = el.text().trim().cleanChapterName(),
                    chapterIndex = i,
                    sourceUrl    = href,
                    isLoaded     = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchToc69shuById failed: ${e.message}", e)
            emptyList()
        }
    }

    /** Nội dung chương 69shu — giống getChap69shu() (dùng fetch thông thường thay browser) */
    private fun fetchChap69shu(url: String): String {
        val doc = fetchGbk(url)
        val content = doc.selectFirst("div.txtnav") ?: return ""
        content.select("h1, div").remove()
        return content.html()
            .replace(Regex("""^ *第\d+章.*?<br>"""), "")
            .replace("(本章完)", "")
            .replace(Regex("""无错版本在.*?吧首发本小说。""", RegexOption.MULTILINE), "")
            .replace(Regex("""本作品由六九.*?理上传~~""", RegexOption.MULTILINE), "")
            .replace(Regex("<br\\s*/?>|\\n"), "\n\n")
            .trim()
    }

    // ==========================================================
    // PRIVATE — Ptwxz / Piaotia (1ptwxz.js)
    // ==========================================================

    private fun fetchDetailPtwxz(url: String): Pair<Book, List<Chapter>> {
        val doc = fetchGb2312(url)
        val title = doc.selectFirst("#content h1")?.text()?.trim() ?: ""
        val author = doc.select("#content table table td")
            .firstOrNull { Regex("作.*者：").containsMatchIn(it.text()) }
            ?.text()?.replace(Regex("作.*者："), "")?.trim() ?: "Không rõ"
        val cover = doc.selectFirst("#content table table a > img[align][hspace][vspace]")
            ?.attr("src") ?: ""
        val description = doc.selectFirst(
            "#content table table div[style]:not([id]):not([onclick])"
        )?.also { it.select("span, a").remove() }?.html() ?: ""

        val localBookId = UUID.nameUUIDFromBytes(url.toByteArray()).toString()
        val chapters = fetchTocPtwxzFromDetailUrl(url, localBookId)
        val book = Book(
            id            = localBookId,
            title         = title,
            author        = author,
            description   = description,
            coverUrl      = cover,
            source        = "crawl",
            sourceUrl     = url,
            totalChapters = chapters.size
        )
        return book to chapters
    }

    private fun fetchTocPtwxzById(id: String, localBookId: String): List<Chapter> {
        if (id.isBlank()) return emptyList()
        return try {
            val prefix = id.toLongOrNull()?.div(1000) ?: return emptyList()
            val url = "$HOST_PTWXZ/html/$prefix/$id/"
            fetchTocPtwxzFromUrl(url, localBookId)
        } catch (e: Exception) {
            Log.e(TAG, "fetchTocPtwxzById failed: ${e.message}", e)
            emptyList()
        }
    }

    private fun fetchTocPtwxzFromDetailUrl(detailUrl: String, localBookId: String): List<Chapter> {
        // bookinfo/{a}/{b}.html → /html/{a}/{b}/
        val tocUrl = detailUrl.replace(
            Regex("""bookinfo/(\d+)/(\d+)\.html$"""),
            "html/$1/$2/"
        ).let { if (it.endsWith("/")) it else "$it/" }
        return fetchTocPtwxzFromUrl(tocUrl, localBookId)
    }

    private fun fetchTocPtwxzFromUrl(url: String, localBookId: String): List<Chapter> {
        val doc = fetchGb2312(url)
        return doc.select("div.centent li > a").mapIndexed { i, el ->
            val href = el.attr("href").let {
                if (it.startsWith("http")) it
                else "$HOST_PTWXZ/${it.trimStart('/')}"
            }
            Chapter(
                id           = UUID.nameUUIDFromBytes(href.toByteArray()).toString(),
                bookId       = localBookId,
                title        = el.text().trim(),
                chapterIndex = i,
                sourceUrl    = href,
                isLoaded     = false
            )
        }
    }

    /** Nội dung chương Ptwxz — giống getChapPtwxz() */
    private fun fetchChapPtwxz(url: String): String {
        val doc = fetchGb2312(url)
        doc.select("h1, div, table, script, center").remove()
        val htm = doc.body().html()
        return htm.replace(Regex("<br\\s*/?>|\\n"), "\n\n").trim()
    }

    // ==========================================================
    // PRIVATE — Fanqie (1fanqie.js — phiên bản public không cần auth)
    // ==========================================================

    private fun fetchChapFanqie(url: String): String {
        // Extract chapterId từ URL kiểu /bookId/chapId/
        val chapId = Regex("""/(\d+)/(\d+)/?$""").find(url)?.groupValues?.get(2) ?: ""
        if (chapId.isBlank()) return ""

        // Thử các endpoint public (không yêu cầu đăng nhập)
        val endpoints = listOf(
            "https://api.langge.cf/content?item_id=$chapId&source=番茄&tab=小说&tone_id=默认音色&version=4.6.29",
            "https://api.doubi.tk/content?item_id=$chapId&source=番茄&tab=小说&tone_id=默认音色&version=4.6.29"
        )
        for (endpoint in endpoints) {
            try {
                val body = rawGet(endpoint)
                val json = org.json.JSONObject(body)
                val content = json.optString("content", "").trim()
                if (content.isNotBlank()) {
                    return content
                        .replace(Regex("""本书源属于.*?企鹅：\d+）"""), "")
                        .replace(Regex("""[\u200B-\u200F\u202A-\u202E\u2060-\u206F\uFEFF]"""), "")
                        .replace(Regex("<br\\s*/?>|\\n"), "\n\n")
                        .trim()
                }
            } catch (e: Exception) {
                Log.w(TAG, "fanqie endpoint failed: $endpoint — ${e.message}")
            }
        }
        return "Không thể tải nội dung chương này."
    }

    private fun fetchTocFanqieById(bookId: String, stvUrl: String, localBookId: String): List<Chapter> {
        if (bookId.isBlank()) return emptyList()
        return try {
            val body = rawGet("https://fanqienovel.com/api/reader/directory/detail?bookId=$bookId")
            val root = org.json.JSONObject(body)
            val volumes = root.getJSONObject("data").getJSONArray("chapterListWithVolume")
            val chapters = mutableListOf<Chapter>()
            var index = 0
            for (volumeIndex in 0 until volumes.length()) {
                val volume = volumes.getJSONArray(volumeIndex)
                for (chapterIndex in 0 until volume.length()) {
                    val item = volume.getJSONObject(chapterIndex)
                    val itemId = item.optString("itemId")
                    if (itemId.isBlank()) continue
                    chapters.add(
                        Chapter(
                            id = UUID.nameUUIDFromBytes("$stvUrl$itemId".toByteArray()).toString(),
                            bookId = localBookId,
                            title = item.optString("title", "Chương ${index + 1}"),
                            chapterIndex = index++,
                            sourceUrl = "${stvUrl.trimEnd('/')}/$itemId/",
                            isLoaded = false
                        )
                    )
                }
            }
            chapters
        } catch (e: Exception) {
            Log.e(TAG, "fetchTocFanqieById failed: ${e.message}", e)
            emptyList()
        }
    }

    // ==========================================================
    // PRIVATE — Qimao (1qimao.js)
    // ==========================================================

    private fun fetchDetailQimao(url: String): Pair<Book, List<Chapter>> {
        val stvUrl = if (url.contains("sangtac") || url.contains("14.225.254.182")) {
            normalizeStvUrl(url)
        } else {
            val id = url.filter { it.isDigit() }
            "$STV_HOST/truyen/qimao/1/$id/"
        }
        return fetchDetailSTV(stvUrl)
    }

    private fun fetchTocQimaoById(bookId: String, localBookId: String): List<Chapter> {
        if (bookId.isBlank()) return emptyList()
        return try {
            val sign = md5Hex("id=$bookId$QIMAO_SECRET")
            val body = rawGet(
                url = "https://api-ks.wtzw.com/api/v1/chapter/chapter-list?id=$bookId&sign=$sign",
                headers = qimaoHeaders()
            )
            val root = org.json.JSONObject(body)
            val list = root.getJSONObject("data").getJSONArray("chapter_lists")
            val chapters = mutableListOf<Chapter>()
            for (i in 0 until list.length()) {
                val chapter = list.getJSONObject(i)
                val chapterId = chapter.optString("id")
                if (chapterId.isBlank()) continue
                val apiPath = "chapterId=$chapterId&id=$bookId"
                chapters.add(
                    Chapter(
                        id = UUID.nameUUIDFromBytes(apiPath.toByteArray()).toString(),
                        bookId = localBookId,
                        title = chapter.optString("title", "Chương ${i + 1}"),
                        chapterIndex = i,
                        sourceUrl = "https://api-bc.wtzw.com/$apiPath",
                        isLoaded = false
                    )
                )
            }
            chapters
        } catch (e: Exception) {
            Log.e(TAG, "fetchTocQimaoById failed: ${e.message}", e)
            emptyList()
        }
    }

    private fun fetchChapQimao(url: String): String {
        val query = url
            .removePrefix("https://api-bc.wtzw.com/")
            .removePrefix("?")
            .ifBlank { return "" }
        val sign = md5Hex("${query.replaceFirst("&", "")}$QIMAO_SECRET")
        val body = rawGet(
            url = "https://api-ks.wtzw.com/api/v1/chapter/content?$query&sign=$sign",
            headers = qimaoHeaders()
        )
        val contentBase64 = org.json.JSONObject(body)
            .getJSONObject("data")
            .getString("content")
        val encryptedHex = Base64.decode(contentBase64, Base64.DEFAULT).toHex()
        if (encryptedHex.length <= 32) return ""
        val iv = encryptedHex.take(32).hexToBytes()
        val cipherText = encryptedHex.drop(32).hexToBytes()
        val key = "32343263636238323330643730396531".hexToBytes()
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
            .replace(Regex("<br\\s*/?>|\\n"), "\n\n")
            .trim()
    }

    // ==========================================================
    // HTTP HELPERS
    // ==========================================================

    private val UA_ANDROID =
        "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val UA_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private fun fetchHtml(url: String, userAgent: String = UA_DESKTOP): Document {
        return try {
            if (url.contains("sangtacviet.app")) {
                fetchHtmlViaOkHttp(url)
            } else {
                Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(TIMEOUT_S.toInt() * 1000)
                    .followRedirects(true)
                    .get()
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchHtml failed cho $url: ${e.message}")
            throw e
        }
    }


    /** Lấy trang mã GBK (69shu) */
    private fun fetchGbk(url: String): Document =
        Jsoup.connect(url)
            .userAgent(UA_DESKTOP)
            .timeout(TIMEOUT_S.toInt() * 1000)
            .followRedirects(true)
            .ignoreHttpErrors(true)
            .parser(org.jsoup.parser.Parser.htmlParser())
            .get().also { it.outputSettings().charset(Charsets.UTF_8) }
            .let {
                // Jsoup không tự detect GBK — phải fetch raw rồi decode
                val bytes = rawGetBytes(url)
                Jsoup.parse(bytes.inputStream(), "GBK", url)
            }

    /** Lấy trang mã GB2312 (Ptwxz) */
    private fun fetchGb2312(url: String): Document {
        val bytes = rawGetBytes(url)
        return Jsoup.parse(bytes.inputStream(), "GB2312", url)
    }

    private fun rawGet(url: String, headers: Map<String, String> = emptyMap()): String {
        val builder = Request.Builder().url(url).header("User-Agent", UA_DESKTOP)
        headers.forEach { (key, value) -> builder.header(key, value) }
        val req = builder.build()
        return client.newCall(req).execute().use { it.body?.string() ?: "" }
    }

    private fun rawGetBytes(url: String): ByteArray {
        val req = Request.Builder().url(url).header("User-Agent", UA_DESKTOP).build()
        return client.newCall(req).execute().use { it.body?.bytes() ?: ByteArray(0) }
    }

    // Helpers extension
    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { w ->
            if (w.isEmpty()) w else w[0].uppercaseChar() + w.substring(1)
        }

    private fun String.cleanChapterName(): String {
        // "1.第23章 ..." → "第23章 ..."
        return replace(Regex("""^\d+\.第(\d+)章"""), "第$1章")
    }

    private fun qimaoHeaders(): Map<String, String> {
        val signSource =
            "app-version=${QIMAO_APP_VERSION}application-id=${QIMAO_APPLICATION_ID}platform=android$QIMAO_SECRET"
        return mapOf(
            "platform" to "android",
            "app-version" to QIMAO_APP_VERSION,
            "application-id" to QIMAO_APPLICATION_ID,
            "sign" to md5Hex(signSource),
            "user-agent" to "webviewversion/0"
        )
    }

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun String.hexToBytes(): ByteArray {
        val clean = trim()
        return ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}

// ============================================================
// TXT FILE PARSER — giữ nguyên, không liên quan đến crawl
// ============================================================

class TxtChapterParser {
    companion object {
        val DEFAULT_CHAPTER_REGEX = Regex(
            pattern = """^(?:Chương|CHƯƠNG|Chapter|CHAPTER|Phần|Part)\s*\d+.*$""",
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
                        chapters.add(build(bookId, currentTitle, currentContent.toString(), chapterIndex++))
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
        if (firstHeadingFound || currentContent.isNotBlank())
            chapters.add(build(bookId, currentTitle, currentContent.toString(), chapterIndex))
        return chapters
    }

    private fun build(bookId: String, title: String, content: String, index: Int) = Chapter(
        id = UUID.randomUUID().toString(), bookId = bookId,
        title = title, content = content.trim(),
        chapterIndex = index, isLoaded = true
    )
}
