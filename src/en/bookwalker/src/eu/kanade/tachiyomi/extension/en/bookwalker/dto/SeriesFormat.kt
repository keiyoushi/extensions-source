package eu.kanade.tachiyomi.extension.en.bookwalker.dto

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class SeriesFormat(private val value: Int) {
    companion object {
        val MANGA = SeriesFormat(1)
        val WEBTOON = SeriesFormat(3)
        val NOVEL = SeriesFormat(2)
        val AUDIOBOOKS = SeriesFormat(4)
    }
}
