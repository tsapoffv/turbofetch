package com.turbofetch.domain.resolver

import com.turbofetch.domain.model.MediaInfo

/**
 * Умеет превратить ссылку конкретной площадки в список прямых ссылок на файлы.
 *
 * Один резолвер — одна площадка. Это позволяет чинить одну площадку, не трогая
 * остальные, и подменять реализацию по мере того, как площадка меняет вёрстку.
 */
interface VideoResolver {

    /** Площадка, которую умеет этот резолвер. */
    val platform: com.turbofetch.domain.model.Source

    /** Разобрать ссылку и вернуть метаданные с вариантами качества. */
    suspend fun resolve(url: String): Result<MediaInfo>
}
