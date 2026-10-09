package eu.kanade.tachiyomi.extension.es.mantrazscan

import kotlinx.serialization.Serializable

@Serializable
class ChapterDatesDto(
    private val dates: Map<String, Long>,
) {
    val chapterDates get() = dates
}
