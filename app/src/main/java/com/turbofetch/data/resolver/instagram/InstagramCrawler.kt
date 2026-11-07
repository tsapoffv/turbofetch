package com.turbofetch.data.resolver.instagram

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Разбор «краулер-вида» публикации Instagram.
 *
 * Instagram отдаёт полезную страницу только боту: с обычным браузерным
 * User-Agent приходит пустая оболочка приложения. Поэтому мы идём с
 * `Googlebot` и вытаскиваем нужное из JSON-блоков `<script data-sjs>`.
 *
 * Разбор вынесен в чистые функции без сети: если Meta поменяет разметку,
 * это будет видно в офлайн-тесте на сохранённом куске страницы.
 *
 * Проверено 7 ноября 2026 на живых страницах.
 */
object InstagramCrawler {

    private val json = Json { ignoreUnknownKeys = true }

    /** Блоки `<script data-sjs>` — в них лежит JSON. */
    private val SCRIPT_BLOCK = Regex(
        """<script[^>]*data-sjs[^>]*>(.*?)</script>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** Обложка публикации в мета-теге. */
    private val OG_IMAGE = Regex(
        """<meta[^>]+property="og:image"[^>]+content="([^"]+)"""",
    )

    /** Один видеофайл публикации. */
    data class Video(
        val url: String,
        val height: Int = 0,
        val width: Int = 0,
        val bitrate: Int = 0,
        /** `progressive` — обычный mp4, `dash` — лучшее представление из MPD. */
        val kind: Kind = Kind.PROGRESSIVE,
    ) {
        enum class Kind { PROGRESSIVE, DASH }

        /**
         * Разрешение для подписи: у вертикальных роликов высота больше
         * ширины (720×1280), и «1280p» вводит в заблуждение — в качестве
         * считаем меньшую сторону, как принято у YouTube.
         */
        val quality: Int
            get() = when {
                width > 0 && height > 0 -> minOf(width, height)
                height > 0 -> height
                width > 0 -> width
                else -> 0
            }
    }

    /** Один элемент публикации: у карусели их несколько. */
    data class Item(
        val pk: Long,
        val code: String,
        val hasAudio: Boolean,
        val isPhoto: Boolean,
        val progressive: Video?,
        val dash: Video?,
        val durationSeconds: Int?,
        /** Обложка: для карусели и видео она тоже есть. */
        val thumbnailUrl: String?,
    )

    /** Разобранная публикация целиком. */
    data class Post(
        val code: String,
        val hasAudio: Boolean,
        val items: List<Item>,
    ) {
        /** Есть ли хоть одно видео в публикации. */
        val hasVideo: Boolean get() = items.any { it.progressive != null || it.dash != null }
    }

    /**
     * Разбирает страницу публикации.
     *
     * @param html ответ Instagram на `/p/<shortcode>/`.
     * @param mediaId числовой id публикации — якорь, по которому ищем
     *   нужный объект среди десятков чужих на той же странице.
     * @return публикация или null, если данных в странице нет.
     */
    fun parse(html: String, mediaId: Long): Post? {
        val needle = "\"pk\":\"$mediaId\""
        // Обложка в og:image есть всегда, а в JSON — не у всех публикаций.
        val fallbackImage = OG_IMAGE.find(html)
            ?.groupValues?.get(1)
            ?.replace("&amp;", "&")
            ?.takeIf { it.isNotBlank() }

        for (match in SCRIPT_BLOCK.findAll(html)) {
            val text = match.groupValues[1]
            if (!text.contains(needle)) continue

            val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: continue
            val anchor = findByPk(root, mediaId) ?: continue

            val items = collectItems(anchor)
            if (items.isEmpty()) continue

            return Post(
                code = items.first().code,
                hasAudio = items.any { it.hasAudio },
                items = items.map { item ->
                    if (item.thumbnailUrl != null) item else item.copy(thumbnailUrl = fallbackImage)
                },
            )
        }
        return null
    }

    /** Ищет объект, у которого `pk` совпадает с нашим. */
    private fun findByPk(element: kotlinx.serialization.json.JsonElement, mediaId: Long): JsonObject? {
        if (element is JsonObject) {
            val pk = element["pk"]?.jsonPrimitive?.longOrNull
            if (pk == mediaId) return element
            element.values.forEach { value ->
                findByPk(value, mediaId)?.let { return it }
            }
        } else if (element is kotlinx.serialization.json.JsonArray) {
            element.forEach { value ->
                findByPk(value, mediaId)?.let { return it }
            }
        }
        return null
    }

    /**
     * Собирает элементы публикации.
     *
     * Данные лежат не на самом объекте с `pk`, а внутри
     * `if_not_gated_logged_out` (или `videoData`), поэтому ищем рекурсивно.
     */
    private fun collectItems(anchor: JsonObject): List<Item> {
        val carousel = findArray(anchor, "carousel_media")
        if (carousel != null) {
            return carousel.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                buildItem(obj)
            }
        }
        return listOfNotNull(buildItem(anchor))
    }

    /** Собирает один элемент: видео, фото или пусто. */
    private fun buildItem(obj: JsonObject): Item? {
        val pk = obj["pk"]?.jsonPrimitive?.longOrNull ?: -1L
        val code = obj["code"]?.jsonPrimitive?.contentOrNullSafe().orEmpty()

        val progressive = findArray(obj, "video_versions")
            ?.firstNotNullOfOrNull { element ->
                val entry = element as? JsonObject ?: return@firstNotNullOfOrNull null
                val url = entry["url"]?.jsonPrimitive?.contentOrNullSafe()
                    ?.replace("\\u0026", "&")
                    ?.takeIf { it.isNotBlank() }
                    ?: return@firstNotNullOfOrNull null
                Video(
                    url = url,
                    height = entry["height"]?.jsonPrimitive?.intOrNullSafe() ?: 0,
                    width = entry["width"]?.jsonPrimitive?.intOrNullSafe() ?: 0,
                    bitrate = entry["bitrate"]?.jsonPrimitive?.intOrNullSafe() ?: 0,
                )
            }

        val dash = findString(obj, "video_dash_manifest")
            ?.let { parseDashManifest(it) }

        val isPhoto = progressive == null && dash == null &&
            findObject(obj, "image_versions2") != null

        // У progressive-файла в JSON нет ни ширины, ни высоты, поэтому
        // разрешение берём у публикации целиком.
        val originalWidth = obj["original_width"]?.jsonPrimitive?.intOrNullSafe() ?: 0
        val originalHeight = obj["original_height"]?.jsonPrimitive?.intOrNullSafe() ?: 0

        return Item(
            pk = pk,
            code = code,
            hasAudio = obj["has_audio"]?.jsonPrimitive?.booleanOrNullSafe() ?: false,
            isPhoto = isPhoto,
            progressive = progressive?.copy(
                width = progressive.width.takeIf { it > 0 } ?: originalWidth,
                height = progressive.height.takeIf { it > 0 } ?: originalHeight,
            ),
            dash = dash,
            durationSeconds = durationOf(progressive?.url ?: dash?.url),
            thumbnailUrl = thumbnailOf(obj),
        )
    }

    /**
     * Разбор MPD-манифеста: выбираем лучшее представление.
     *
     * У Instagram это настоящая лестница битрейтов (проверено: 0.51 / 1.04 /
     * 1.97 / 3.14 Мбит/с при 720×1280), а progressive отдаёт один-единственный
     * файл в 480×854. Поэтому DASH — это и есть «лучшее качество».
     *
     * Важно: в URL внутри манифеста стоит `&amp;` вместо `&`. С сырой формой
     * CDN отдаёт 403, поэтому экранирование снимаем.
     */
    fun parseDashManifest(manifest: String): Video? {
        val pairs = REPRESENTATION.findAll(manifest).mapNotNull { match ->
            val attrs = match.groupValues[1]
            val baseUrl = match.groupValues[2].trim()
            if (baseUrl.isEmpty()) return@mapNotNull null
            val width = attrs.intAttr("width")
            val height = attrs.intAttr("height")
            val bandwidth = attrs.intAttr("bandwidth")
            Triple(width, height, baseUrl) to bandwidth
        }.toList()

        if (pairs.isEmpty()) return null

        val best = pairs.maxWithOrNull(
            compareBy<Pair<Triple<Int, Int, String>, Int>> { it.first.first * it.first.second }
                .thenBy { it.second },
        ) ?: return null

        val (size, _) = best
        return Video(
            url = size.third.replace("&amp;", "&"),
            width = size.first,
            height = size.second,
            bitrate = best.second,
            kind = Video.Kind.DASH,
        )
    }

    private val REPRESENTATION = Regex(
        """<Representation([^>]*?)>\s*<BaseURL>(.*?)</BaseURL>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** Рекурсивно ищет массив по ключу. */
    private fun findArray(element: kotlinx.serialization.json.JsonElement, key: String): kotlinx.serialization.json.JsonArray? {
        when (element) {
            is JsonObject -> {
                element[key]?.let { value ->
                    if (value is kotlinx.serialization.json.JsonArray) return value
                }
                element.values.forEach { value ->
                    findArray(value, key)?.let { return it }
                }
            }

            is kotlinx.serialization.json.JsonArray -> {
                element.forEach { value ->
                    findArray(value, key)?.let { return it }
                }
            }

            else -> Unit
        }
        return null
    }

    /** Рекурсивно ищет строку по ключу. */
    private fun findString(element: kotlinx.serialization.json.JsonElement, key: String): String? {
        when (element) {
            is JsonObject -> {
                (element[key] as? JsonPrimitive)?.let { primitive ->
                    if (primitive.isString) {
                        return primitive.content.takeIf { it.isNotBlank() }
                    }
                }
                element.values.forEach { value ->
                    findString(value, key)?.let { return it }
                }
            }

            is kotlinx.serialization.json.JsonArray -> {
                element.forEach { value ->
                    findString(value, key)?.let { return it }
                }
            }

            else -> Unit
        }
        return null
    }

    /**
     * Длительность ролика.
     *
     * Instagram прячет её в query-параметре `efg` — это base64 от JSON,
     * в котором есть `duration_s`. Разбирать не нужно, если не повезло:
     * тогда длительность останется неизвестной, а не выдуманной.
     */
    fun durationOf(url: String?): Int? {
        val raw = url?.substringAfter("efg=", "")?.takeIf { it.isNotEmpty() }
            ?.substringBefore("&") ?: return null
        // Instagram кодирует знак «=» как «%3D» — без этой замены
        // строгий base64-декодер споткнётся.
        val efg = raw.replace("%3D", "=").replace("%3d", "=")
        val pattern = Regex("\"duration_s\"\\s*:\\s*([0-9.]+)")
        // В efg встречается и обычный, и url-безопасный base64 — пробуем оба.
        for (decoder in listOf(
            { value: String -> java.util.Base64.getUrlDecoder().decode(padBase64(value)) },
            { value: String -> java.util.Base64.getDecoder().decode(padBase64(value)) },
        )) {
            val decoded = runCatching { String(decoder(efg)) }.getOrNull() ?: continue
            val seconds = pattern.find(decoded)
                ?.groupValues?.get(1)?.toDouble()?.toInt()
            if (seconds != null && seconds > 0) return seconds
        }
        return null
    }

    private fun padBase64(value: String): String =
        value + "=".repeat((4 - value.length % 4) % 4)

    private fun String.intAttr(name: String): Int =
        Regex("$name=\"([0-9]+)\"").find(this)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /** Обложка публикации: первая картинка из `image_versions2.candidates`. */
    private fun thumbnailOf(obj: JsonObject): String? {
        val candidates = findObject(obj, "image_versions2")
            ?.get("candidates")?.jsonArray
            ?.firstNotNullOfOrNull { candidate ->
                (candidate as? JsonObject)?.get("url")
                    ?.jsonPrimitive?.contentOrNullSafe()
            }
        return candidates?.replace("\\u0026", "&")
    }

    /** Рекурсивно ищет объект по ключу. */
    private fun findObject(element: kotlinx.serialization.json.JsonElement, key: String): JsonObject? {
        when (element) {
            is JsonObject -> {
                (element[key] as? JsonObject)?.let { return it }
                element.values.forEach { value ->
                    findObject(value, key)?.let { return it }
                }
            }

            is kotlinx.serialization.json.JsonArray -> {
                element.forEach { value ->
                    findObject(value, key)?.let { return it }
                }
            }

            else -> Unit
        }
        return null
    }
}

private fun JsonPrimitive.contentOrNullSafe(): String? =
    if (this is JsonPrimitive && isString) content else null

private fun JsonPrimitive.intOrNullSafe(): Int? =
    if (this is JsonPrimitive) content.toIntOrNull() else null

private fun JsonPrimitive.booleanOrNullSafe(): Boolean? =
    if (this is JsonPrimitive) content.toBooleanStrictOrNull() else null
