package com.turbofetch.domain.model

/**
 * Темы оформления.
 *
 * В main-ветке тема одна — «Warm Paper», спокойная бумажная, светлая
 * и тёмная. Шуточная тема «ТУРБОВАЙБКОД» живёт только в fun-ветке.
 */
enum class AppTheme(
    val title: String,
    val description: String,
) {
    WARM_PAPER(
        title = "Warm Paper",
        description = "Бумага, чернила, тишина",
    ),
    ;

    /** Тёмный вариант этой темы. */
    val hasDarkVariant: Boolean get() = true
}
