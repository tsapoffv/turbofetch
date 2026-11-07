package com.turbofetch.domain.model

/**
 * Темы оформления.
 *
 * - [WARM_PAPER] — спокойная «бумажная» тема, светлая и тёмная.
 * - [TURBOBAYBEKOD] — шуточная тема для fun-ветки: неон, эмодзи
 *   и все худшие приёмы нейросетевого интерфейса.
 */
enum class AppTheme(
    val title: String,
    val description: String,
) {
    WARM_PAPER(
        title = "Warm Paper",
        description = "Бумага, чернила, тишина",
    ),
    TURBOBAYBEKOD(
        title = "ТУРБОВАЙБКОД",
        description = "Неон, эмодзи и полный трэш",
    ),
    ;

    /** Тёмный вариант этой темы. */
    val hasDarkVariant: Boolean
        get() = this != TURBOBAYBEKOD
}
