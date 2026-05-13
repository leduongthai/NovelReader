package com.example.novelreader.data.remote.upload

// ============================================================
// EXTERNAL FILE UPLOAD SERVICE
//
// Why: Firebase free tier has NO Storage and RTDB has a 1 MB
// payload limit per write. TXT novel files can be megabytes.
//
// Solution: upload to a FREE anonymous hosting service and store
// only the returned URL in RTDB.
//
// Included implementations:
//   • TransferShUploadService  — transfer.sh  (14-day retention)
//   • FileIoUploadService      — file.io       (one-download, 14 days)
//   • ManualUrlUploadService   — user pastes URL themselves (no upload)
//
// DI wires TransferShUploadService by default. You can swap in
// FileIoUploadService or ManualUrlUploadService in AppModules.kt.
// ============================================================

// ─────────────────────────────────────────────────────────────
// INTERFACE
// ─────────────────────────────────────────────────────────────

interface ExternalFileUploadService {

    /**
     * Upload [fileBytes] to an external host.
     *
     * @param fileName Suggested file name (e.g. "novel.txt").
     * @param fileBytes Raw bytes of the file.
     * @return [Result.success] containing a direct download URL,
     *         or [Result.failure] with a descriptive exception.
     */
    suspend fun upload(fileName: String, fileBytes: ByteArray): Result<String>
}
