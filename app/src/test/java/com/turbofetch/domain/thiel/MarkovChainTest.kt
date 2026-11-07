package com.turbofetch.domain.thiel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MarkovChainTest {

    private val corpus = listOf(
        "The future is simply the set of all moments yet to come.",
        "Every moment in business happens only once.",
        "Every time we create something new, we go from 0 to 1.",
        "Monopoly is the condition of every successful business.",
        "All happy companies are different.",
    )

    @Test
    fun `с фиксированным зерном результат воспроизводится`() {
        val first = MarkovChain(corpus, random = Random(42)).generate()
        val second = MarkovChain(corpus, random = Random(42)).generate()

        assertEquals(first, second)
    }

    @Test
    fun `разные зёрна дают разные цитаты`() {
        val first = MarkovChain(corpus, random = Random(1)).generate()
        val second = MarkovChain(corpus, random = Random(2)).generate()

        assertTrue("Обе цитаты совпали: $first", first != second)
    }

    @Test
    fun `цитата состоит только из слов корпуса`() {
        val chain = MarkovChain(corpus, random = Random(7))
        // Знаки препинания отбрасываем: в корпусе слова лежат с запятыми
        // и точками, а в цитате — с теми же знаками, но сравнивать надо
        // по голому слову.
        val known = corpus
            .flatMap { it.split(Regex("\\s+")) }
            .map { it.replace(Regex("[^A-Za-z0-9']"), "") }
            .filter { it.isNotEmpty() }
            .toSet()

        repeat(30) {
            val quote = chain.generate()
            val words = quote.split(Regex("\\s+"))
            words.forEach { word ->
                val bare = word.replace(Regex("[^A-Za-z0-9']"), "")
                if (bare.isNotEmpty()) {
                    assertTrue("Слово '$bare' не из корпуса", known.contains(bare))
                }
            }
        }
    }

    @Test
    fun `пустой корпус не роняет генерацию`() {
        val quote = MarkovChain(emptyList(), random = Random(1)).generate()

        assertEquals("", quote)
    }

    @Test
    fun `слишком короткие фразы отбрасываются`() {
        val chain = MarkovChain(listOf("Hi.", "Ok."), random = Random(1))

        // Фразы короче окна цепи не дают переходов — генератор не должен падать.
        assertEquals("", chain.generate())
    }

    @Test
    fun `корпус Тиля не пуст и покрывает три темы`() {
        assertTrue(ThielCorpus.STARTUP.isNotEmpty())
        assertTrue(ThielCorpus.POLITICS.isNotEmpty())
        assertTrue(ThielCorpus.ESCHATOLOGY.isNotEmpty())

        assertEquals(
            ThielCorpus.STARTUP.size + ThielCorpus.POLITICS.size + ThielCorpus.ESCHATOLOGY.size,
            ThielCorpus.ALL.size,
        )
    }

    @Test
    fun `в каждой теме Тиля есть ключевые слова`() {
        assertTrue(
            ThielCorpus.ESCHATOLOGY.any { it.contains("Antichrist") },
        )
        assertTrue(
            ThielCorpus.ESCHATOLOGY.any { it.contains("Katechon") },
        )
        assertTrue(
            ThielCorpus.POLITICS.any { it.contains("democracy") },
        )
        assertTrue(
            ThielCorpus.STARTUP.any { it.contains("Monopoly") },
        )
    }
}
