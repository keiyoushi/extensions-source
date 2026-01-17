package eu.kanade.tachiyomi.extension.en.yorai

import kotlinx.serialization.Serializable

@Serializable
class Browse(
    val series: List<Series>,
    val pagination: Pagination,
) {
    @Serializable
    class Pagination(
        val page: Int,
        val totalPages: Int,
    )
}

@Serializable
class Series(
    val id: String,
    val title: String,
    val coverImage: String,
)

@Serializable
class DetailSeries(
    val description: String?,
    val author: String?,
    val artist: String?,
    val status: String,
    val genres: List<String>,
    val tags: List<String>,
    val type: String,
    val year: Int?,
)

@Serializable
class Chapters(
    val seriesId: String,
    val chapters: List<Chapter>,
) {
    @Serializable
    class Chapter(
        val number: Float,
        val title: String,
        val sources: List<Source>,
        val defaultSource: String,
        val releaseDate: String,
    ) {
        @Serializable
        class Source(
            val id: String,
            val name: String,
            val quality: String,
        )
    }
}

@Serializable
class DetailedChapter(
    val source: String,
    val pages: List<Page>,
) {
    @Serializable
    class Page(
        val number: Int,
        val url: String,
    )
}
