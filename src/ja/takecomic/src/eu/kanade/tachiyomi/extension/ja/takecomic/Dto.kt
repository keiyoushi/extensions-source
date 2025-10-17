package eu.kanade.tachiyomi.extension.ja.takecomic

import kotlinx.serialization.Serializable

@Serializable
class ApiResponse(
    val series: SeriesData,
)

@Serializable
class SeriesData(
    val summary: SeriesSummary,
    val episodes: List<Episode> = emptyList(),
)

@Serializable
class SeriesSummary(
    val name: String,
    val description: String,
    val author: List<Author>,
    val images: List<SeriesImage>,
    val tag: List<Tag>,
)

@Serializable
class Author(
    val name: String,
)

@Serializable
class SeriesImage(
    val url: String,
)

@Serializable
class Tag(
    val name: String,
)

@Serializable
class Episode(
    val id: String,
    val title: String,
    val datePublished: Long,
)

@Serializable
class DescriptionNode(
    val children: List<DescriptionChild>,
)

@Serializable
class DescriptionChild(
    val text: String,
)
