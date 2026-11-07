package com.turbofetch.domain.model

/** Площадка-источник ссылки. */
enum class Source {
    YOUTUBE,
    TIKTOK,
    X,
    INSTAGRAM,
    SOUNDCLOUD,
    UNKNOWN,
    ;

    companion object {
        fun fromHost(host: String): Source = when {
            host.contains("youtube.com") || host.contains("youtu.be") -> YOUTUBE
            host.contains("tiktok.com") -> TIKTOK
            // Instagram и SoundCloud проверяем раньше X: подстрока «x.com»
            // в их доменах не встречается, но порядок держим осмысленным.
            host.contains("instagram.com") || host.contains("instagr.am") -> INSTAGRAM
            host.contains("soundcloud.com") -> SOUNDCLOUD
            host.contains("x.com") || host.contains("twitter.com") -> X
            else -> UNKNOWN
        }
    }
}
