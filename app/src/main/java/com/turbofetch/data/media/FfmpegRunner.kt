package com.turbofetch.data.media

import android.content.Context
import com.turbofetch.core.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Запуск ffmpeg, собранного под Android.
 *
 * Исполняемый файл лежит в `nativeLibraryDir` под именем `libffmpegcli.so` —
 * только с таким именем PackageManager его распаковывает на диск, и только
 * из nativeLibraryDir разрешено запускать файлы (с Android 10 запуск из
 * домашней папки приложения запрещён политикой W^X).
 *
 * Бинарник собран для arm64-v8a с выравниванием страниц 16 КБ и с
 * кодировщиком mp3 (libmp3lame, LGPL).
 */
class FfmpegRunner(context: Context) {

    private val appContext = context.applicationContext

    /** Есть ли ffmpeg на этой архитектуре. */
    val isAvailable: Boolean
        get() = executable.exists()

    private val executable: File
        get() = File(appContext.applicationInfo.nativeLibraryDir, EXECUTABLE_NAME)

    /**
     * Перекодирует аудио в mp3.
     *
     * @param input исходный файл (видео или аудио)
     * @param output результат в формате mp3
     */
    suspend fun convertToMp3(
        input: File,
        output: File,
        bitrate: String = "192k",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isAvailable) {
            return@withContext Result.failure(
                IllegalStateException(
                    "ffmpeg собран только под arm64-v8a, а устройство: " +
                        "${android.os.Build.SUPPORTED_ABIS.firstOrNull()}",
                ),
            )
        }
        if (!input.exists()) {
            return@withContext Result.failure(IllegalStateException("Исходный файл не найден"))
        }

        val nativeDir = appContext.applicationInfo.nativeLibraryDir
        output.parentFile?.mkdirs()

        val command = listOf(
            executable.absolutePath,
            "-hide_banner",
            "-loglevel", "error",
            "-y",
            "-i", input.absolutePath,
            "-vn",
            "-c:a", "libmp3lame",
            "-b:a", bitrate,
            output.absolutePath,
        )

        runCatching {
            val process = ProcessBuilder(command).apply {
                environment()["LD_LIBRARY_PATH"] = nativeDir
                redirectErrorStream(true)
            }.start()

            val outputText = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()

            if (code != 0) {
                Log.e("ffmpeg завершился с кодом $code: $outputText")
                error("ffmpeg не смог перекодировать файл: ${outputText.take(300)}")
            }
            Log.i("ffmpeg: mp3 готов, ${output.length()} байт")
            Unit
        }
    }

    companion object {
        const val EXECUTABLE_NAME = "libffmpegcli.so"
    }
}
