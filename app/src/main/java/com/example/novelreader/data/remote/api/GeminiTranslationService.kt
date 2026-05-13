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

@Singleton
class GeminiTranslationService @Inject constructor() {

    companion object {
        private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODEL = "gemini-2.0-flash-exp"
        private const val MAX_OUTPUT_TOKENS = 65536
        private const val TIMEOUT_SECONDS = 120L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun translateChapter(
        chapterContent: String,
        prompt: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (apiKey.isBlank()) throw IllegalArgumentException("API key không được để trống")
            if (chapterContent.isBlank()) throw IllegalArgumentException("Nội dung chương trống")

            val url = "$GEMINI_BASE_URL/$MODEL:generateContent?key=$apiKey"

            val requestBody = JSONObject().apply {
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })

                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", chapterContent) })
                        })
                    })
                })

                put("generationConfig", JSONObject().apply {
                    put("maxOutputTokens", MAX_OUTPUT_TOKENS)
                    put("temperature", 0.3)
                    put("topP", 0.95)
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

            val json = JSONObject(responseBody)
            val candidates = json.getJSONArray("candidates")
            if (candidates.length() == 0) throw Exception("Gemini không trả về kết quả")

            val candidate = candidates.getJSONObject(0)

            val finishReason = candidate.optString("finishReason", "STOP")
            if (finishReason == "SAFETY") throw Exception("Nội dung bị chặn bởi bộ lọc an toàn")
            if (finishReason == "MAX_TOKENS") {
            }

            candidate
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
        }
    }


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
