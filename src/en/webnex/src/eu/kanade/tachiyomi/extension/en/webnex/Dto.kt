package eu.kanade.tachiyomi.extension.en.webnex

import kotlinx.serialization.Serializable

@Serializable
class ReaderDto(
    val pages: List<String>,
)

@Serializable
class ReaderPropsDto(
    val series: SeriesRefDto,
    val pagesUrl: String,
)

@Serializable
class SeriesRefDto(
    val href: String,
)

@Serializable
class TagsDto(
    val genres: List<String>,
    val tags: List<String>,
)
