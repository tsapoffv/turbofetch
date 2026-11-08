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
                // В карточку задачи ошибка попадает целиком — берём только первую
                // строку и обрезаем, иначе она занимает пол-экрана.
                val reason = outputText.lineSequence()
                    .firstOrNull { it.isNotBlank() }
                    ?.substringAfter("Error opening input file ")
                    ?.substringBefore("Error opening input files")
                    ?.take(120)
                    ?: "код $code"
                error("ffmpeg: $reason")
            }
            Log.i("ffmpeg: mp3 готов, ${output.length()} байт")
            Unit
        }
    }

    /**
     * Перекодирует видео в анимированный GIF.
     *
     * Нужно для гифок из X: площадка отдаёт анимацию mp4-конвертом, и
     * такой файл половина плееров не показывает.
     *
     * Как это делается: кадры уменьшаются до 480 px по ширине, частота
     * ограничивается 15 кадрами в секунду (иначе GIF весит десятки
     * мегабайт), и цвета сводятся к палитре в 128 цветов — два прохода,
     * поэтому фильтр состоит из `split`, `palettegen` и `paletteuse`.
     *
     * @param width максимальная ширина кадра, пиксели
     * @param fps кадров в секунду в результате
     */
    suspend fun convertToGif(
        input: File,
        output: File,
        width: Int = 480,
        fps: Int = 15,
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

        // Фильтр-граф собирается отдельно (GifFilter) — там же и объяснение,
        // почему кадры отбираются по времени, а не по номеру.
        val filter = GifFilter.build(width = width, fps = fps)

        val command = listOf(
            executable.absolutePath,
            "-hide_banner",
            "-loglevel", "error",
            "-y",
            "-i", input.absolutePath,
            "-filter_complex", filter,
            "-loop", "0",
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
                Log.e("ffmpeg (gif) завершился с кодом $code: $outputText")
                val reason = outputText.lineSequence()
                    .firstOrNull { it.isNotBlank() }
                    ?.take(120)
                    ?: "код $code"
                error("ffmpeg: $reason")
            }
            Log.i("ffmpeg: gif готов, ${output.length()} байт")
            Unit
        }
    }

    companion object {
        const val EXECUTABLE_NAME = "libffmpegcli.so"
    }
}
