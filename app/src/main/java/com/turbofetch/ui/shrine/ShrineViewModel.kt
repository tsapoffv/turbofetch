package com.turbofetch.ui.shrine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.turbofetch.domain.thiel.MarkovChain
import com.turbofetch.domain.thiel.ThielCorpus
import java.util.Calendar
import kotlin.random.Random

/**
 * Темы святилища. Каждая — свой корпус и своя цепь Маркова,
 * поэтому цитаты в разных темах звучат по-разному.
 */
enum class ShrineTopic(
    val title: String,
    val subtitle: String,
) {
    ALL(
        title = "Всё",
        subtitle = "Полный корпус",
    ),
    STARTUP(
        title = "Бизнес",
        subtitle = "«Zero to One», 2014",
    ),
    POLITICS(
        title = "Политика",
        subtitle = "Cato Unbound, 2009",
    ),
    ESCHATOLOGY(
        title = "Апокалипсис",
        subtitle = "Лекции, 2025",
    ),
    ;

    fun corpus(): List<String> = when (this) {
        ALL -> ThielCorpus.ALL
        STARTUP -> ThielCorpus.STARTUP
        POLITICS -> ThielCorpus.POLITICS
        ESCHATOLOGY -> ThielCorpus.ESCHATOLOGY
    }

    companion object {
        /** Темы с отдельным корпусом — из них набирается цитата для «Всего». */
        val OWN_CORPUS: List<ShrineTopic> = listOf(STARTUP, POLITICS, ESCHATOLOGY)
    }
}

/**
 * Откровение: цитата и тема, из которой она собрана.
 *
 * Топик печатается под цитатой — чтобы было видно, откуда она пришла.
 */
data class Revelation(
    val text: String,
    val topic: ShrineTopic,
)

/**
 * Состояние святилища.
 *
 * **Цитата дня** — одна, наверху, меняется раз в сутки (зерно — номер дня
 * в году). Под ней — «Открыть ворота», добавляющая цитаты в список.
 *
 * **Список ограничен пятью цитатами.** Старые уходят, новые встают в конец.
 * При каждом запуске приложения святылище начинается заново: список пустой,
 * показана только цитата дня. Хранить откровения между запусками смысла
 * нет — это генератор шуток, а не архив.
 */
class ShrineViewModel : ViewModel() {

    var topic by mutableStateOf(ShrineTopic.ALL)
        private set

    /** Цитата дня: одна, над списком. */
    var quoteOfTheDay by mutableStateOf<Revelation?>(null)
        private set

    /** Откровения по темам: тема → список (не длиннее [MAX_QUOTES]). */
    private val revelations = mutableMapOf<ShrineTopic, MutableList<Revelation>>()

    /** Счётчик — сколько всего откровений выдано за сеанс. */
    var revealed by mutableStateOf(0)
        private set

    /**
     * Переключает тему. Список для новой темы пуст — наполнять его сразу
     * незачем: цитата дня уже показана, остальное user запрашивает сам.
     */
    fun selectTopic(newTopic: ShrineTopic) {
        topic = newTopic
    }

    /** Откровения по текущей теме (список для отрисовки). */
    fun revelationsFor(current: ShrineTopic = topic): List<Revelation> =
        revelations[current].orEmpty()

    /**
     * Добавляет ещё одно откровение — кнопка «Открыть ворота».
     *
     * Каждое нажатие даёт новую вязь: случайность здесь несидирована.
     * Список не растёт бесконечно: при переполнении самая старая цитата
     * уходит, иначе экран превращается в простыню.
     */
    fun reveal() {
        val current = topic
        // Для «Всего» тема выбирается случайно — так в ленте встречаются
        // цитаты со всех сторон Тиля.
        val source = if (current == ShrineTopic.ALL) {
            ShrineTopic.OWN_CORPUS.random(random)
        } else {
            current
        }

        val chain = MarkovChain(source.corpus(), order = 2, random = Random(System.nanoTime()))
        val quote = chain.generate()
        if (quote.isBlank()) return

        val bucket = revelations.getOrPut(current) { mutableListOf() }
        bucket.add(Revelation(quote, source))
        while (bucket.size > MAX_QUOTES) {
            bucket.removeAt(0)
        }
        revealed++
    }

    /**
     * Готовит цитату дня. Зерно — номер дня в году, поэтому в течение дня
     * цитата одна и та же, а на следующий день меняется.
     */
    fun prepareQuoteOfTheDay() {
        if (quoteOfTheDay != null) return

        val source = ShrineTopic.OWN_CORPUS[dayOfYear % ShrineTopic.OWN_CORPUS.size]
        val chain = MarkovChain(source.corpus(), order = 2, random = Random(dayOfYear.toLong()))
        val quote = chain.generate()
        if (quote.isBlank()) return

        quoteOfTheDay = Revelation(quote, source)
    }

    /** Номер дня в году: меняется раз в сутки. */
    private val dayOfYear: Int
        get() = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

    /** Генератор для тем «Всего»: тема цитаты выбирается случайно. */
    private val random = Random(System.nanoTime())

    private companion object {
        /** Сколько цитат максимум в списке. */
        const val MAX_QUOTES = 5
    }
}
