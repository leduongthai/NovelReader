package com.example.novelreader.data.remote.api

import com.example.novelreader.domain.model.ChapterTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiTranslationService @Inject constructor() {

    private class TranslationTooLongException(message: String) : Exception(message)

    private data class ChunkTranslationResult(
        val source: String,
        val translation: ChapterTranslation
    )

    companion object {
        private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val MODEL_CANDIDATES = listOf(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-3-flash-preview"
        )
        private const val MAX_OUTPUT_TOKENS = 8192
        private const val TRANSLATION_CHUNK_CHARS = 1800
        private const val TIMEOUT_SECONDS = 120L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun translateChapter(
        chapterTitle: String,
        chapterContent: String,
        prompt: String,
        apiKey: String
    ): Result<ChapterTranslation> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanedApiKey = apiKey.trim()
            if (cleanedApiKey.isBlank()) throw IllegalArgumentException("API key không được để trống")
            if (chapterContent.isBlank()) throw IllegalArgumentException("Nội dung chương trống")

            var lastModelError: Throwable? = null
            for (model in MODEL_CANDIDATES) {
                try {
                    return@runCatching requestTranslation(
                        model = model,
                        chapterTitle = chapterTitle,
                        chapterContent = chapterContent,
                        prompt = prompt,
                        apiKey = cleanedApiKey
                    )
                } catch (e: Exception) {
                    if (!e.isModelUnavailable()) throw e
                    lastModelError = e
                }
            }

            throw lastModelError ?: Exception("Không tìm được model Gemini hỗ trợ dịch")
        }
    }

    suspend fun translateChapterTitle(
        chapterTitle: String,
        prompt: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanedApiKey = apiKey.trim()
            if (cleanedApiKey.isBlank()) throw IllegalArgumentException("API key khong duoc de trong")
            if (chapterTitle.isBlank()) return@runCatching ""

            var lastModelError: Throwable? = null
            for (model in MODEL_CANDIDATES) {
                try {
                    return@runCatching requestTitleTranslation(
                        model = model,
                        chapterTitle = chapterTitle,
                        prompt = prompt,
                        apiKey = cleanedApiKey
                    )
                } catch (e: Exception) {
                    if (!e.isModelUnavailable()) throw e
                    lastModelError = e
                }
            }

            throw lastModelError ?: Exception("Khong tim duoc model Gemini ho tro dich")
        }
    }

    private fun requestTitleTranslation(
        model: String,
        chapterTitle: String,
        prompt: String,
        apiKey: String
    ): String {
        val userPrompt = buildString {
            appendLine("Dich ten chuong tieu thuyet sang tieng Viet.")
            appendLine("Chi tra ve JSON hop le, khong markdown, khong giai thich.")
            appendLine("Schema: {\"title\":\"...\"}")
            appendLine()
            appendLine("Prompt tuy chinh cua nguoi dung:")
            appendLine(prompt.trim().ifBlank { "Dich ten chuong nay sang tieng Viet." })
            appendLine()
            appendLine("Ten chuong:")
            append(chapterTitle)
        }
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", userPrompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 256)
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
            })
        }

        val request = Request.Builder()
            .url("$GEMINI_BASE_URL/$model:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw Exception("Khong nhan duoc phan hoi tu Gemini")
            if (!response.isSuccessful) {
                val errorMessage = runCatching {
                    JSONObject(responseBody).getJSONObject("error").getString("message")
                }.getOrDefault("Loi Gemini API: ${response.code}")
                throw Exception(errorMessage)
            }
            val parts = JSONObject(responseBody)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: throw Exception("Gemini khong tra ve ten chuong")
            val rawText = buildString {
                for (i in 0 until parts.length()) {
                    val partText = parts.getJSONObject(i).optString("text")
                    if (partText.isNotBlank()) append(partText)
                }
            }.trim()
            return parseTranslatedTitle(rawText).ifBlank { chapterTitle }
        }
    }

    private fun requestTranslation(
        model: String,
        chapterTitle: String,
        chapterContent: String,
        prompt: String,
        apiKey: String
    ): ChapterTranslation {
        val chunks = splitContentForTranslation(chapterContent)
        var translatedTitle = ""
        val translatedResults = chunks.flatMapIndexed { index, chunk ->
            requestTranslationChunkWithRetry(
                model = model,
                chapterTitle = chapterTitle,
                chapterContent = chunk,
                prompt = prompt,
                apiKey = apiKey,
                chunkIndex = index + 1,
                totalChunks = chunks.size
            )
        }

        val translatedParts = translatedResults.map { result ->
            val translation = result.translation
            validateTranslationLength(source = result.source, translation = translation)
            if (translatedTitle.isBlank()) translatedTitle = translation.title
            translation.content
        }

        return ChapterTranslation(
            title = translatedTitle.ifBlank { chapterTitle },
            content = translatedParts.joinToString("\n\n").trim()
        )
    }

    private fun requestTranslationChunkWithRetry(
        model: String,
        chapterTitle: String,
        chapterContent: String,
        prompt: String,
        apiKey: String,
        chunkIndex: Int,
        totalChunks: Int
    ): List<ChunkTranslationResult> {
        return try {
            listOf(
                ChunkTranslationResult(
                    source = chapterContent,
                    translation = requestTranslationChunk(
                        model = model,
                        chapterTitle = chapterTitle,
                        chapterContent = chapterContent,
                        prompt = prompt,
                        apiKey = apiKey,
                        chunkIndex = chunkIndex,
                        totalChunks = totalChunks
                    )
                )
            )
        } catch (e: TranslationTooLongException) {
            if (chapterContent.length <= 700) {
                throw Exception("Gemini van cat ban dich du doan da rat ngan. Hay thu lai sau hoac doi model/API key.")
            }
            splitChunkInHalf(chapterContent).flatMap { part ->
                requestTranslationChunkWithRetry(
                    model = model,
                    chapterTitle = chapterTitle,
                    chapterContent = part,
                    prompt = prompt,
                    apiKey = apiKey,
                    chunkIndex = chunkIndex,
                    totalChunks = totalChunks
                )
            }
        }
    }

    private fun requestTranslationChunk(
        model: String,
        chapterTitle: String,
        chapterContent: String,
        prompt: String,
        apiKey: String,
        chunkIndex: Int,
        totalChunks: Int
    ): ChapterTranslation {
        val userPrompt = buildString {
            appendLine("Ban la bo may dich tieu thuyet. Bat buoc lam dung cac quy tac sau:")
            appendLine("- Dich ca ten chuong va noi dung sang tieng Viet tu nhien.")
            appendLine("- Giu nguyen y nghia, ten rieng, xuong dong va khong tu y rut gon.")
            appendLine("- Chi tra ve JSON hop le, khong markdown, khong giai thich, khong chu thich.")
            appendLine("- JSON dung chinh xac schema: {\"title\":\"...\",\"content\":\"...\"}")
            if (totalChunks > 1) {
                appendLine("- Day la phan $chunkIndex/$totalChunks cua cung mot chuong. Chi dich phan nay, khong tom tat, khong them loi dan.")
                appendLine("- Van tra ve title la ten chuong da dich, content chi la ban dich cua phan hien tai.")
            }
            appendLine()
            appendLine("Prompt tuy chinh cua nguoi dung:")
            appendLine(prompt.trim().ifBlank { "Dich chuong nay sang tieng Viet." })
            appendLine()
            appendLine("Ten chuong can dich:")
            appendLine(chapterTitle)
            appendLine()
            appendLine("Noi dung can dich:")
            append(chapterContent)
        }
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", userPrompt) })
                    })
                })
            })

            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", MAX_OUTPUT_TOKENS)
                put("temperature", 0.3)
                put("topP", 0.95)
                put("responseMimeType", "application/json")
            })

            put("safetySettings", JSONArray().apply {
                listOf(
                    "HARM_CATEGORY_HARASSMENT",
                    "HARM_CATEGORY_HATE_SPEECH",
                    "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                    "HARM_CATEGORY_DANGEROUS_CONTENT"
                ).forEach { category ->
                    put(JSONObject().apply {
                        put("category", category)
                        put("threshold", "BLOCK_ONLY_HIGH")
                    })
                }
            })
        }

        val request = Request.Builder()
            .url("$GEMINI_BASE_URL/$model:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
                ?: throw Exception("Không nhận được phản hồi từ Gemini")

            if (!response.isSuccessful) {
                val errorMessage = runCatching {
                    JSONObject(responseBody).getJSONObject("error").getString("message")
                }.getOrDefault("Lỗi Gemini API: ${response.code}")
                throw Exception(errorMessage)
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
                ?: throw Exception(
                    json.optJSONObject("promptFeedback")?.optString("blockReason")
                        ?.let { "Gemini chặn nội dung: $it" }
                        ?: "Gemini không trả về kết quả"
                )
            if (candidates.length() == 0) throw Exception("Gemini không trả về kết quả")

            val candidate = candidates.getJSONObject(0)
            val finishReason = candidate.optString("finishReason", "STOP")
            if (finishReason == "MAX_TOKENS") {
                throw TranslationTooLongException("Doan dich qua dai, dang chia nho hon de thu lai.")
            }
            if (finishReason == "SAFETY") throw Exception("Nội dung bị chặn bởi bộ lọc an toàn")

            val parts = candidate
                .optJSONObject("content")
                ?.optJSONArray("parts")
                ?: throw Exception("Gemini không trả về nội dung văn bản")

            val rawText = buildString {
                for (i in 0 until parts.length()) {
                    val partText = parts.getJSONObject(i).optString("text")
                    if (partText.isNotBlank()) append(partText)
                }
            }.trim()

            if (rawText.isBlank()) {
                throw Exception("Gemini trả về rỗng (finishReason=$finishReason)")
            }
            return parseTranslation(rawText, fallbackTitle = chapterTitle)
        }
    }

    private fun splitContentForTranslation(text: String): List<String> {
        val normalized = text.trim()
        if (normalized.length <= TRANSLATION_CHUNK_CHARS) return listOf(normalized)

        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        val paragraphs = normalized
            .split(Regex("\\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        for (paragraph in paragraphs) {
            if (paragraph.length > TRANSLATION_CHUNK_CHARS) {
                if (current.toString().isNotBlank()) {
                    chunks += current.toString().trim()
                    current.clear()
                }

                var start = 0
                while (start < paragraph.length) {
                    val end = (start + TRANSLATION_CHUNK_CHARS).coerceAtMost(paragraph.length)
                    chunks += paragraph.substring(start, end).trim()
                    start = end
                }
                continue
            }

            if (current.length + paragraph.length + 2 > TRANSLATION_CHUNK_CHARS) {
                if (current.toString().isNotBlank()) {
                    chunks += current.toString().trim()
                    current.clear()
                }
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(paragraph)
        }

        if (current.toString().isNotBlank()) chunks += current.toString().trim()
        return chunks.ifEmpty { listOf(normalized) }
    }

    private fun splitChunkInHalf(text: String): List<String> {
        val clean = text.trim()
        if (clean.length <= 1) return listOf(clean)

        val midpoint = clean.length / 2
        val min = (clean.length * 0.35f).toInt()
        val max = (clean.length * 0.65f).toInt()
        val splitAt = listOf(
            clean.lastIndexOf("\n\n", midpoint),
            clean.lastIndexOf('\n', midpoint),
            clean.lastIndexOf('.', midpoint),
            clean.lastIndexOf('。', midpoint),
            clean.lastIndexOf('!', midpoint),
            clean.lastIndexOf('?', midpoint)
        ).filter { it in min..max }.maxOrNull() ?: midpoint

        val left = clean.substring(0, splitAt + 1).trim()
        val right = clean.substring((splitAt + 1).coerceAtMost(clean.length)).trim()
        val parts = listOf(left, right).filter { it.isNotBlank() }
        if (parts.size == 2) return parts

        return listOf(
            clean.substring(0, midpoint).trim(),
            clean.substring(midpoint).trim()
        ).filter { it.isNotBlank() }
    }

    private fun validateTranslationLength(source: String, translation: ChapterTranslation) {
        val sourceChars = source.count { !it.isWhitespace() }
        val translatedChars = translation.content.count { !it.isWhitespace() }
        if (sourceChars >= 1500 && translatedChars < sourceChars * 0.55f) {
            throw IllegalArgumentException("Ban dich qua ngan so voi chuong goc. Hay bam dich lai.")
        }
    }

    private fun parseTranslation(rawText: String, fallbackTitle: String): ChapterTranslation {
        val cleaned = rawText
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val objectText = cleaned
            .substringAfter("{", missingDelimiterValue = cleaned)
            .let { if (it == cleaned) it else "{$it" }
            .substringBeforeLast("}", missingDelimiterValue = cleaned)
            .let { if (it.endsWith("}")) it else "$it}" }

        return runCatching {
            val json = JSONObject(objectText)
            ChapterTranslation(
                title = json.optString("title").ifBlank { fallbackTitle },
                content = json.optString("content")
                    .ifBlank { throw IllegalArgumentException("Missing translated content") }
            )
        }.getOrElse {
            throw IllegalArgumentException("Gemini khong tra ve JSON ban dich hop le. Hay bam dich lai.")
        }
    }

    private fun parseTranslatedTitle(rawText: String): String {
        val cleaned = rawText
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val objectText = cleaned
            .substringAfter("{", missingDelimiterValue = cleaned)
            .let { if (it == cleaned) it else "{$it" }
            .substringBeforeLast("}", missingDelimiterValue = cleaned)
            .let { if (it.endsWith("}")) it else "$it}" }

        return runCatching {
            JSONObject(objectText).optString("title")
        }.getOrDefault(cleaned).trim()
    }

    private fun Throwable.isModelUnavailable(): Boolean {
        val message = this.message.orEmpty()
        return message.contains("not found", ignoreCase = true) ||
                message.contains("not supported", ignoreCase = true) ||
                message.contains("not available", ignoreCase = true) ||
                message.contains("404")
    }

    suspend fun validateApiKey(apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanedApiKey = apiKey.trim()
            if (cleanedApiKey.isBlank()) return@runCatching false

            val body = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", "Hello") })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply { put("maxOutputTokens", 10) })
            }

            for (model in MODEL_CANDIDATES) {
                val request = Request.Builder()
                    .url("$GEMINI_BASE_URL/$model:generateContent")
                    .addHeader("x-goog-api-key", cleanedApiKey)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return@runCatching true
                    val errorMessage = response.body?.string().orEmpty()
                    if (!Exception(errorMessage).isModelUnavailable()) return@runCatching false
                }
            }
            false
        }
    }
}
