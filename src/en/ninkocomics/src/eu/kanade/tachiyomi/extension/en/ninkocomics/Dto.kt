package eu.kanade.tachiyomi.extension.en.ninkocomics

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.Serializable
import org.jsoup.nodes.Element

// The ComicSeries JSON-LD of a comic page
@Serializable
class SeriesDto(
    private val name: String,
    private val image: String? = null,
    private val genre: List<String> = emptyList(),
    private val author: AuthorDto? = null,
    private val alternateName: List<String> = emptyList(),
) {
    fun toSManga(path: String, synopsis: Element?, schedule: String?, mangaStatus: Int) = SManga.create().apply {
        url = path
        title = name
        thumbnail_url = image
        author = this@SeriesDto.author?.name
        genre = this@SeriesDto.genre.joinToString().ifEmpty { null }
        status = mangaStatus
        description = MarkdownDescription.Builder()
            .info(schedule)
            .description(synopsis)
            .altTitles(alternateName)
            .build()
    }
}

@Serializable
class AuthorDto(
    val name: String,
)
