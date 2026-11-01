package eu.kanade.tachiyomi.extension.en.webnex

import kotlinx.serialization.Serializable

@Serializable
class ReaderDto(
    val pages: List<String>,
)

@Serializable
class WebFetchDto(
    val status: Int,
    val body: String,
)

@Serializable
class ReaderPropsDto(
    val series: SeriesRefDto,
)

@Serializable
class SeriesRefDto(
    val id: String,
)

@Serializable
class TagsDto(
    val genres: List<String>,
    val tags: List<String>,
)
