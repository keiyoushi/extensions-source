package eu.kanade.tachiyomi.multisrc.natsuid

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.toJsonString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

@Serializable
class Term(
    val name: String,
    val slug: String,
    val taxonomy: String,
)

@Serializable
class Manga(
    val id: Int,
    val slug: String,
    val title: Rendered,
    val content: Rendered,
    @SerialName("_embedded")
    val embedded: Embedded,
) {
    fun toSManga(appendId: Boolean = false) = SManga.create().apply {
        url = MangaUrl(id, slug).toJsonString()
        title = Parser.unescapeEntities(this@Manga.title.rendered, false)
        description = buildString {
            append(Jsoup.parseBodyFragment(content.rendered).wholeText())
            if (appendId) {
                append("\n\nID: $id")
            }
        }
        thumbnail_url = embedded.featuredMedia.firstOrNull()?.sourceUrl
        author = embedded.getTerms("series-author").joinToString()
        artist = embedded.getTerms("artist").joinToString()
        genre = buildSet {
            addAll(embedded.getTerms("genre"))
            addAll(embedded.getTerms("type"))
        }.joinToString()
        status = with(embedded.getTerms("status")) {
            when {
                contains("Ongoing") -> SManga.ONGOING
                contains("Completed") -> SManga.COMPLETED
                contains("Cancelled") -> SManga.CANCELLED
                contains("On Hiatus") -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
        initialized = true
    }
}

@Serializable
class Embedded(
    @SerialName("wp:featuredmedia")
    val featuredMedia: List<FeaturedMedia>,
    @SerialName("wp:term")
    private val terms: List<List<Term>>,
) {
    fun getTerms(type: String): List<String> {
        return terms.find { it.getOrNull(0)?.taxonomy == type }?.map { it.name } ?: emptyList()
    }
}

@Serializable
class FeaturedMedia(
    @SerialName("source_url")
    val sourceUrl: String,
)

@Serializable
class Rendered(
    val rendered: String,
)

@Serializable
class MangaUrl(
    val id: Int,
    val slug: String,
)
