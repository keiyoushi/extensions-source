package eu.kanade.tachiyomi.extension.es.novelaschafas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class FeedResponseDto(private val feed: FeedDto) {
    // The feed of the one post asked for by its path
    val content get() = feed.entry.single().content.t
}

@Serializable
class FeedDto(val entry: List<EntryDto>)

@Serializable
class EntryDto(val content: TextDto)

@Serializable
class TextDto(@SerialName("\$t") val t: String)
