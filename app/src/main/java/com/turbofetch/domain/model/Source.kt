package com.turbofetch.domain.model

/** Площадка-источник ссылки. */
enum class Source {
    YOUTUBE,
    TIKTOK,
    X,
    UNKNOWN,
    ;

    companion object {
        fun fromHost(host: String): Source = when {
            host.contains("youtube.com") || host.contains("youtu.be") -> YOUTUBE
            host.contains("tiktok.com") -> TIKTOK
            host.contains("x.com") || host.contains("twitter.com") -> X
            else -> UNKNOWN
        }
    }
}
