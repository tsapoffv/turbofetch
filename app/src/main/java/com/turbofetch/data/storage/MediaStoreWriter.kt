package com.turbofetch.data.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Кладёт готовый файл в общее хранилище: видео — в `Movies/TurboFetch`,
 * аудио — в `Music/TurboFetch`. Файлы сразу видны в галерее и музыкальных плеерах.
 *
 * На Android 10+ (API 29) запись идёт через MediaStore и не требует разрешений на хранилище.
 * На Android 8–9 нужен `WRITE_EXTERNAL_STORAGE` и прямой доступ к публичной папке.
 */
class MediaStoreWriter(private val context: Context) {

    suspend fun save(
        source: File,
        displayName: String,
        mimeType: String,
        isAudio: Boolean,
    ): String = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(source, displayName, mimeType, isAudio)
        } else {
            saveViaLegacyFile(source, displayName, isAudio)
        }
    }

    private fun saveViaMediaStore(
        source: File,
        displayName: String,
        mimeType: String,
        isAudio: Boolean,
    ): String {
        val resolver = context.contentResolver
        val collection = if (isAudio) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relativePath = if (isAudio) {
            "${Environment.DIRECTORY_MUSIC}/TurboFetch"
        } else {
            "${Environment.DIRECTORY_MOVIES}/TurboFetch"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri: Uri = resolver.insert(collection, values)
            ?: error("Не удалось создать запись в MediaStore")

        resolver.openOutputStream(uri)?.use { out ->
            source.inputStream().use { input -> input.copyTo(out) }
        } ?: error("Не удалось открыть поток записи")

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return uri.toString()
    }

    private fun saveViaLegacyFile(source: File, displayName: String, isAudio: Boolean): String {
        val baseDir = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val dir = File(
            Environment.getExternalStoragePublicDirectory(baseDir),
            "TurboFetch",
        )
        if (!dir.exists() && !dir.mkdirs()) error("Не удалось создать папку ${dir.absolutePath}")

        val target = File(dir, displayName)
        source.inputStream().use { input ->
            target.outputStream().use { out -> input.copyTo(out) }
        }
        return Uri.fromFile(target).toString()
    }
}
