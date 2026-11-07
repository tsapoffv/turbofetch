package com.turbofetch.ui.queue

import androidx.annotation.DrawableRes
import com.turbofetch.R
import com.turbofetch.domain.model.Source

/**
 * Значок площадки для карточки задачи.
 *
 * Брендовых знаков в Material Icons нет, поэтому у каждой площадки свой
 * вектор в `res/drawable`. Векторы одноцветные — цвет им задаёт тема.
 */
@DrawableRes
fun Source.iconRes(): Int = when (this) {
    Source.YOUTUBE -> R.drawable.ic_source_youtube
    Source.TIKTOK -> R.drawable.ic_source_tiktok
    Source.X -> R.drawable.ic_source_x
    Source.INSTAGRAM -> R.drawable.ic_source_instagram
    Source.SOUNDCLOUD -> R.drawable.ic_source_soundcloud
    Source.SPOTIFY -> R.drawable.ic_source_spotify
    Source.UNKNOWN -> R.drawable.ic_source_link
}
