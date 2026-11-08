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

    private companion object {
        /** Буфер копирования: 256 КБ. */
        const val COPY_BUFFER_SIZE = 256 * 1024
    }

    suspend fun save(
        source: File,
        displayName: String,
        mimeType: String,
        isAudio: Boolean,
        isImage: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(source, displayName, mimeType, isAudio, isImage)
        } else {
            saveViaLegacyFile(source, displayName, isAudio, isImage)
        }
    }

    private fun saveViaMediaStore(
        source: File,
        displayName: String,
        mimeType: String,
        isAudio: Boolean,
        isImage: Boolean,
    ): String {
        val resolver = context.contentResolver
        val collection = when {
            isImage -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            isAudio -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relativePath = when {
            isImage -> "${Environment.DIRECTORY_PICTURES}/TurboFetch"
            isAudio -> "${Environment.DIRECTORY_MUSIC}/TurboFetch"
            else -> "${Environment.DIRECTORY_MOVIES}/TurboFetch"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        // Файл с таким именем мог остаться от прошлой загрузки. Ничего не
        // удаляем — подбираем свободное имя, как это делает сама галерея
        // («Трек (1).m4a»). На части прошивок вставка с занятым именем
        // вообще не возвращает ответ и зависает.
        values.put(
            MediaStore.MediaColumns.DISPLAY_NAME,
            freeDisplayName(resolver, collection, displayName, relativePath),
        )

        val uri: Uri = resolver.insert(collection, values)
            ?: error("Не удалось создать запись в MediaStore")

        resolver.openOutputStream(uri)?.use { out ->
            source.inputStream().use { input ->
                // Буфер 256 КБ вместо стандартных 8 КБ: запись идёт через
                // FUSE-мост MediaStore, и каждый вызов write() там стоит
                // дорого. На файле в 200 МБ это 25 600 вызовов вместо 800.
                input.copyTo(out, COPY_BUFFER_SIZE)
            }
        } ?: error("Не удалось открыть поток записи")

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return uri.toString()
    }

    /**
     * Подбирает имя, которого ещё нет в нашей папке.
     *
     * «Трек.m4a» → «Трек (1).m4a» → «Трек (2).m4a» и так далее. Старые
     * файлы остаются на месте: удалять их без явного разрешения юзера
     * нельзя.
     */
    private fun freeDisplayName(
        resolver: android.content.ContentResolver,
        collection: Uri,
        displayName: String,
        relativePath: String,
    ): String {
        val taken = runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(relativePath),
                null,
            )?.use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(0) ?: continue)
                    }
                }
            } ?: emptySet()
        }.getOrDefault(emptySet())

        if (displayName !in taken) return displayName

        val dot = displayName.lastIndexOf('.')
        val stem = if (dot > 0) displayName.substring(0, dot) else displayName
        val extension = if (dot > 0) displayName.substring(dot) else ""
        var counter = 1
        while (true) {
            val candidate = "$stem ($counter)$extension"
            if (candidate !in taken) return candidate
            counter++
        }
    }

    private fun saveViaLegacyFile(
        source: File,
        displayName: String,
        isAudio: Boolean,
        isImage: Boolean,
    ): String {
        val baseDir = when {
            isImage -> Environment.DIRECTORY_PICTURES
            isAudio -> Environment.DIRECTORY_MUSIC
            else -> Environment.DIRECTORY_MOVIES
        }
        val dir = File(
            Environment.getExternalStoragePublicDirectory(baseDir),
            "TurboFetch",
        )
        if (!dir.exists() && !dir.mkdirs()) error("Не удалось создать папку ${dir.absolutePath}")

        val target = File(dir, displayName)
        source.inputStream().use { input ->
            target.outputStream().use { out -> input.copyTo(out, COPY_BUFFER_SIZE) }
        }
        return Uri.fromFile(target).toString()
    }

}
