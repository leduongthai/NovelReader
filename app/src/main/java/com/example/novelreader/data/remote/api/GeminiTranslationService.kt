package com.example.novelreader.data.remote.api

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

// ============================================================
// GEMINI API SERVICE
// Uses user's own API key — no backend proxy needed.
// Sends the ENTIRE chapter as a single request (no chunking).
// ============================================================

@Singleton
class GeminiTranslationService @Inject constructor() {

    companion object {
        private const val GEMINI_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODEL = "gemini-2.0-flash-exp"
        private const val MAX_OUTPUT_TOKENS = 65536   // Gemini 2.0 Flash supports large output
        private const val TIMEOUT_SECONDS = 120L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Translates an entire chapter in ONE API call.
     *
     * @param chapterContent  Full raw text of the chapter
     * @param prompt          User's custom translation prompt (system instruction)
     * @param apiKey          User's personal Gemini API key
     * @return               Translated text or error
     */
    suspend fun translateChapter(
        chapterContent: String,
        prompt: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (apiKey.isBlank()) throw IllegalArgumentException("API key không được để trống")
            if (chapterContent.isBlank()) throw IllegalArgumentException("Nội dung chương trống")

            val url = "$GEMINI_BASE_URL/$MODEL:generateContent?key=$apiKey"

            // Build request body following Gemini v1beta API spec
            val requestBody = JSONObject().apply {
                // System instruction = user's translation prompt
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })

                // User message = the chapter content
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", chapterContent) })
                        })
                    })
                })

                // Generation config
                put("generationConfig", JSONObject().apply {
                    put("maxOutputTokens", MAX_OUTPUT_TOKENS)
                    put("temperature", 0.3)     // Lower = more faithful translation
                    put("topP", 0.95)
                })

                // Safety settings — relaxed for novel content (may contain violence/romance)
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
                .url(url)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: throw Exception("Không nhận được phản hồi từ Gemini")

            if (!response.isSuccessful) {
                val errorMessage = runCatching {
                    JSONObject(responseBody).getJSONObject("error").getString("message")
                }.getOrDefault("Lỗi API: ${response.code}")
                throw Exception(errorMessage)
            }

            // Parse response: candidates[0].content.parts[0].text
            val json = JSONObject(responseBody)
            val candidates = json.getJSONArray("candidates")
            if (candidates.length() == 0) throw Exception("Gemini không trả về kết quả")

            val candidate = candidates.getJSONObject(0)

            // Check finish reason
            val finishReason = candidate.optString("finishReason", "STOP")
            if (finishReason == "SAFETY") throw Exception("Nội dung bị chặn bởi bộ lọc an toàn")
            if (finishReason == "MAX_TOKENS") {
                // Partial result — still usable, just warn
            }

            candidate
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
        }
    }

    /**
     * Validates that the given API key is functional by sending a minimal test request.
     */
    suspend fun validateApiKey(apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$GEMINI_BASE_URL/$MODEL:generateContent?key=$apiKey"
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
            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        }
    }
}
