package eu.kanade.tachiyomi.extension.en.bookwalker.dto

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class ChapterType(private val value: Int) {
    companion object {
        val VOLUMES = ChapterType(1)
        val CHAPTERS = ChapterType(2)
    }
}
