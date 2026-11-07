package com.turbofetch.domain.thiel

import kotlin.random.Random

/**
 * Цепь Маркова по словам.
 *
 * Идея: корпус реплик склеивается в один непрерывный поток слов, для
 * каждой пары «предыдущее слово → текущее» запоминается, какие слова шли
 * после. Цепочка блуждает по этим переходам и останавливается на границе
 * фразы — так цитата заканчивается полно, а не обрывком.
 *
 * Одна деталь решает всё. Биграмм в корпусе Тиля почти нет повторов
 * (≈8%), поэтому чистая цепь второго порядка почти всегда идёт по
 * исходной цитате дословно — смешивания не происходит. Чтобы цитата
 * звучала как настоящая, но была сшита из разных, в цепь добавлен
 * [jumpProbability]: с такой вероятностью цепочка перепрыгивает в
 * произвольное место другой фразы. Получается правдоподобная вязь —
 * ровно то, что нужно святыне.
 *
 * На выходе — цитата в духе автора, которую он никогда не говорил.
 *
 * @param order длина «окна» цепи. 2 — биграммы, читаются как настоящая речь.
 * @param jumpProbability как часто цепочка перескакивает в другую фразу.
 * @param random генератор: с фиксированным зерном результат воспроизводим.
 */
class MarkovChain(
    corpus: List<String>,
    private val order: Int = 2,
    private val jumpProbability: Double = 0.18,
    private val random: Random = Random.Default,
) {

    /** Слова корпуса, разбитые по фразам. */
    private val sentences: List<List<String>> = corpus
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { sentence ->
            sentence.split(WHITESPACE)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        .filter { it.size >= order + MIN_TAIL }

    /**
     * Непрерывный поток слов: корпус повторён [REPEATS] раз подряд.
     * Именно он даёт переходам возможность перескочить с одной цитаты
     * на другую.
     *
     * Фразы склеены **без разделителя**: последнее слово одной фразы
     * связано с первым словом следующей. Иначе цепочка не сможет
     * перескочить с цитаты на цитату — а это и есть весь смысл.
     */
    private val stream: List<String> = buildList {
        repeat(REPEATS) {
            sentences.forEach { words -> addAll(words) }
        }
    }

    /** Начала фраз: с них цепочка стартует, чтобы цитата звучала как начало. */
    private val starts: List<List<String>> = sentences.map { it.take(order) }

    /** Переходы: пара слов → список слов, которые шли следом. */
    private val transitions: Map<List<String>, List<String>> = buildMap {
        for (i in order until stream.size) {
            val key = stream.subList(i - order, i)
            val existing = getOrPut(key) { mutableListOf() }
            (existing as MutableList<String>).add(stream[i])
        }
    }

    /**
     * Генерирует одну цитату.
     *
     * Перебирает несколько попыток: цепочка должна закончиться границей
     * фразы, а не упереться в потолок длины. Иначе получится обрывок.
     */
    fun generate(maxWords: Int = 20): String {
        if (sentences.isEmpty()) return ""

        for (attempt in 0 until ATTEMPTS) {
            val words = mutableListOf<String>()
            var state = starts.random(random)
            words += state

            while (words.size < maxWords) {
                // Прыжок в другую фразу — источник смешивания.
                if (random.nextDouble() < jumpProbability) {
                    val next = jump(state.last())
                    if (next == null) break
                    words += next
                    state = listOf(state.last(), next).takeLast(order)
                    if (isSentenceEnd(next)) return words.joinToString(" ")
                    continue
                }

                val options = transitions[state] ?: break
                val next = options.random(random)
                words += next
                state = (state.drop(1) + next).takeLast(order)

                // Остановились на границе фразы — цитата закрыта.
                if (isSentenceEnd(next)) return words.joinToString(" ")
            }
        }

        // Ни одна попытка не дошла до границы фразы — отдаём целую
        // исходную фразу: лучше настоящая цитата, чем обрывок.
        return sentences.random(random).joinToString(" ")
    }

    /**
     * Прыжок: слово, которое в корпусе идёт **сразу после** [current].
     *
     * Стык грамматический — «капитализм и» + «конкуренция» читается,
     * а «капитализм» + «власть» уже нет. Именно ради этого и нужна
     * карта «слово → что за ним стоит».
     */
    private fun jump(current: String): String? {
        val continuations = nextWords[current] ?: return null
        return continuations.random(random)
    }

    /**
     * Карта «слово → слова, которые за ним стояли в корпусе».
     * Используется для прыжков: стык всегда получается осмысленным.
     */
    private val nextWords: Map<String, List<String>> = buildMap {
        sentences.forEach { words ->
            for (i in 0 until words.size - 1) {
                val key = words[i]
                val existing = getOrPut(key) { mutableListOf() }
                (existing as MutableList<String>).add(words[i + 1])
            }
        }
    }

    /**
     * Слово заканчивает фразу, если несёт точку, вопросительный знак
     * или многоточие. Иначе цитата выглядит оборванной.
     */
    private fun isSentenceEnd(word: String): Boolean =
        word.endsWith('.') || word.endsWith('!') || word.endsWith('?') ||
            word.endsWith('…')

    private companion object {
        val WHITESPACE = Regex("\\s+")

        /** Минимум хвоста: обрывок из трёх слов цитатой не выглядит. */
        const val MIN_TAIL = 4

        /** Сколько раз корпус повторяется в потоке. */
        const val REPEATS = 40

        /** Сколько раз пробуем построить цепочку до конца фразы. */
        const val ATTEMPTS = 60
    }
}
