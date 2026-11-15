package eu.kanade.tachiyomi.extension.en.manhwa18dash

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

@Serializable
class SeriesListDto(val series: List<SeriesDto>)

@Serializable
class SeriesDto(
    private val title: String,
    @SerialName("alt_titles") private val altTitles: String,
    val slug: String,
    val genres: List<String>,
    private val author: String,
    private val status: String,
    private val cover: String,
) {
    // Same matching as the site's catalogue: text in title, author or alternative titles, all selected genres
    fun matches(term: String, status: String?, genres: List<String>): Boolean {
        val keys = this.genres.map(::genreKey)
        return listOf(title, author, altTitles).any { it.lowercase().contains(term) } &&
            (status == null || this.status.lowercase() == status) &&
            keys.containsAll(genres)
    }

    fun toSManga(cdnUrl: HttpUrl) = SManga.create().apply {
        url = "/manga/$slug"
        title = this@SeriesDto.title
        thumbnail_url = cdnUrl.resolve(cover)?.toString()
    }
}

@Serializable
class SeriesDataDto(
    private val chapters: List<String>,
    private val stats: StatsDto,
) {
    val chapterCount get() = chapters.size
    val views get() = stats.views
    val lastUpdate get() = stats.lastUpdate
}

@Serializable
class StatsDto(
    val views: Long,
    @SerialName("last_update") val lastUpdate: String? = null,
)
