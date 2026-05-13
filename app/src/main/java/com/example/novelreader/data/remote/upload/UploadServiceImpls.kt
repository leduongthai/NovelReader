package com.example.novelreader.data.remote.upload

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================
// TRANSFER.SH UPLOAD SERVICE (DEFAULT)
//
// • Free, anonymous, no account required.
// • Files expire after 14 days.
// • Max file size: 10 GB (more than enough for .txt novels).
// • Returns a direct download URL.
// • Simple HTTP PUT — no SDK needed.
// ============================================================

@Singleton
class TransferShUploadService @Inject constructor() : ExternalFileUploadService {

    companion object {
        private const val BASE_URL = "https://transfer.sh"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS    = 60_000
    }

    override suspend fun upload(fileName: String, fileBytes: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val safeFileName = fileName
                    .replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
                    .ifBlank { "novel.txt" }

                val url = URL("$BASE_URL/$safeFileName")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    doOutput = true
                    setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                    setRequestProperty("Max-Downloads", "1000")
                    setRequestProperty("Max-Days", "14")
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout    = READ_TIMEOUT_MS
                }

                conn.outputStream.use { it.write(fileBytes) }

                val code = conn.responseCode
                if (code !in 200..299) {
                    throw Exception("Transfer.sh HTTP $code — ${conn.responseMessage}")
                }

                val responseUrl = InputStreamReader(conn.inputStream)
                    .use { it.readText() }
                    .trim()

                require(responseUrl.startsWith("http")) {
                    "Transfer.sh trả về URL không hợp lệ: $responseUrl"
                }
                responseUrl
            }
        }
}

// ============================================================
// FILE.IO UPLOAD SERVICE (ALTERNATIVE)
//
// • Free, anonymous.
// • Files auto-deleted after first download OR 14 days.
// • Returns JSON: {"success":true,"link":"https://file.io/xxx"}
// • Use this when you want the link to expire after one use.
// ============================================================

@Singleton
class FileIoUploadService @Inject constructor() : ExternalFileUploadService {

    companion object {
        private const val UPLOAD_URL = "https://file.io/?expires=14d"
        private const val BOUNDARY   = "NovelReaderBoundary"
    }

    override suspend fun upload(fileName: String, fileBytes: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_").ifBlank { "novel.txt" }

                val url = URL(UPLOAD_URL)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$BOUNDARY")
                    connectTimeout = 15_000
                    readTimeout    = 60_000
                }

                DataOutputStream(conn.outputStream).use { out ->
                    out.writeBytes("--$BOUNDARY\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$safeFileName\"\r\n")
                    out.writeBytes("Content-Type: text/plain\r\n\r\n")
                    out.write(fileBytes)
                    out.writeBytes("\r\n--$BOUNDARY--\r\n")
                }

                val code = conn.responseCode
                if (code !in 200..299) throw Exception("file.io HTTP $code")

                val json = InputStreamReader(conn.inputStream).use { it.readText() }
                // Minimal JSON parse — avoids adding Gson/Moshi dependency
                val linkRegex = Regex("\"link\"\\s*:\\s*\"([^\"]+)\"")
                val match = linkRegex.find(json)
                    ?: throw Exception("file.io: link không có trong response")
                val link = match.groupValues[1]
                require(link.startsWith("http")) { "file.io URL không hợp lệ: $link" }
                link
            }
        }
}

// ============================================================
// MANUAL URL SERVICE
//
// No upload logic — the URL is provided externally by the user
// (e.g. from Google Drive, Mega, Dropbox, etc.).
//
// Usage: inject this when you want the UI to show a text field
// asking for the URL instead of picking a file.
//
// This is automatically what happens when the user doesn't pick
// a file in the SharedBookViewModel upload flow.
// ============================================================

@Singleton
class ManualUrlUploadService @Inject constructor() : ExternalFileUploadService {

    override suspend fun upload(fileName: String, fileBytes: ByteArray): Result<String> =
        Result.failure(
            UnsupportedOperationException(
                "ManualUrlUploadService không tải file lên — hãy cung cấp URL trực tiếp."
            )
        )
}
