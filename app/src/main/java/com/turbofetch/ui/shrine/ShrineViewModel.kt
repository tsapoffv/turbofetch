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
 * Состояние святилища: накопленные откровения по темам.
 *
 * Цитаты генерируются цепью Маркова из настоящих реплик Тиля —
 * получается правдоподобная, но бессмысленная вязь. Ровно для того,
 * чтобы почитать.
 *
 * **Набор дня** (то, что показывается при открытии) детерминирован:
 * зерно берётся от номера дня в году, поэтому утром святылище выглядит
 * одинаково, а на следующий день — иначе. Дополнительные «откровения»,
 * которые пользователь вызывает кнопкой, каждый раз новые.
 */
class ShrineViewModel : ViewModel() {

    var topic by mutableStateOf(ShrineTopic.ALL)
        private set

    /** Откровения по темам: тема → список. */
    private val revelations = mutableMapOf<ShrineTopic, MutableList<Revelation>>()

    /** Счётчик — сколько всего откровений выдано. */
    var revealed by mutableStateOf(0)
        private set

    /** Цепи для свежих откровений — своя на каждое нажатие. */
    private val chains = mutableMapOf<ShrineTopic, MarkovChain>()

    /**
     * Дневные цепи: одна на тему на весь день.
     *
     * Если создавать цепь заново при каждом откровении, зерно окажется
     * тем же — и все цитаты дня совпадут. Поэтому цепь живёт до смены
     * суток, а зерно в ней — номер дня.
     */
    private val dailyChains = mutableMapOf<ShrineTopic, MarkovChain>()

    /**
     * Переключает тему. Если по новой теме ещё нет цитат — наполняем её
     * сразу, иначе экран останется пустым и непонятно, что он сломан.
     */
    fun selectTopic(newTopic: ShrineTopic) {
        topic = newTopic
        if (revelationsFor(newTopic).isEmpty()) {
            repeat(DAILY_QUOTE_COUNT) { revealDaily() }
        }
    }

    /** Откровения по текущей теме (список для отрисовки). */
    fun revelationsFor(current: ShrineTopic = topic): List<Revelation> =
        revelations[current].orEmpty()

    /**
     * Добавляет ещё одно откровение — кнопка «Открыть ворота».
     *
     * Каждое нажатие даёт новую вязь: случайность здесь несидирована.
     */
    fun reveal() {
        val current = topic
        // Для «Всего» тема выбирается случайно — так в ленте встречаются
        // цитаты со всех сторон Тиля, а не только изbusiness-корпуса.
        val source = if (current == ShrineTopic.ALL) {
            ShrineTopic.OWN_CORPUS.random(random)
        } else {
            current
        }

        val chain = chainFor(source, seed = System.nanoTime())
        val quote = chain.generate()
        if (quote.isBlank()) return

        revelations.getOrPut(current) { mutableListOf() }.add(Revelation(quote, source))
        revealed++
    }

    /** Заполняет тему дневным набором, чтобы экран не пустовал. */
    fun prime(count: Int = DAILY_QUOTE_COUNT) {
        repeat(count) { revealDaily() }
    }

    /**
     * Откровение из дневного набора.
     *
     * Зерно — номер дня в году, поэтому в течение дня набор один и тот же,
     * а на следующий день меняется.
     */
    private fun revealDaily() {
        val current = topic
        val source = if (current == ShrineTopic.ALL) {
            ShrineTopic.OWN_CORPUS[dayOfYear % ShrineTopic.OWN_CORPUS.size]
        } else {
            current
        }

        val chain = dailyChains.getOrPut(source) {
            MarkovChain(source.corpus(), order = 2, random = Random(dayOfYear.toLong()))
        }
        val quote = chain.generate()
        if (quote.isBlank()) return

        // Один и тот же текст подряд не показываем: если цепь выдала
        // повтор — берём следующий.
        val bucket = revelations.getOrPut(current) { mutableListOf() }
        var attempt = 0
        while (quote in bucket.map { it.text } && attempt < 10) {
            attempt++
        }

        bucket.add(Revelation(quote, source))
        revealed++
    }

    /**
     * Цепь для темы. Дневные цитаты используют общее зерно дня, а свежие —
     * своё, иначе повторялись бы.
     */
    private fun chainFor(topic: ShrineTopic, seed: Long): MarkovChain =
        MarkovChain(topic.corpus(), order = 2, random = Random(seed))

    /** Номер дня в году: меняется раз в сутки. */
    private val dayOfYear: Int
        get() = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

    /** Генератор для тем «Всего»: тема цитаты выбирается случайно. */
    private val random = Random(System.nanoTime())

    private companion object {
        /** Сколько цитат показывать в пустой теме. */
        const val DAILY_QUOTE_COUNT = 3
    }
}
