package eu.kanade.tachiyomi.extension.es.hmanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE_TIME

@Serializable
class Post(
    private val slug: String,
    private val title: Rendered,
    @SerialName("date_gmt") private val dateGmt: String? = null,
    private val content: Rendered? = null,
    @SerialName("_embedded") private val embedded: Embedded? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/$slug/"
        title = Parser.unescapeEntities(this@Post.title.rendered, false)
        thumbnail_url = embedded?.featuredMedia?.firstOrNull()?.sourceUrl
    }

    fun toSMangaDetails() = toSManga().apply {
        val terms = embedded?.terms.orEmpty().flatten()
        fun names(taxonomy: String) = terms.filter { it.taxonomy == taxonomy }.map { Parser.unescapeEntities(it.name, false) }
        author = names("post_tag").joinToString().ifEmpty { null }
        genre = names("category").joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .field("Serie", names("series"))
            .build()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    fun toSChapter() = SChapter.create().apply {
        url = "/$slug/"
        name = "Capítulo"
        date_upload = dateFormat.tryParseDateTime(dateGmt, ZoneOffset.UTC)
    }

    // The reader takes its pages from a script in the post: pages=["...", ...];
    fun pageUrls(): List<String> {
        val script = Jsoup.parseBodyFragment(content!!.rendered).selectFirst("script:containsData(pages=)")!!.data()
        return PAGES_REGEX.find(script)!!.groupValues[1].parseAs()
    }
}

private val PAGES_REGEX = Regex("""pages=(\[.*?])""")

@Serializable
class Rendered(val rendered: String)

@Serializable
class Embedded(
    @SerialName("wp:featuredmedia") val featuredMedia: List<Media>? = null,
    @SerialName("wp:term") val terms: List<List<Term>>? = null,
)

@Serializable
class Media(@SerialName("source_url") val sourceUrl: String? = null)

@Serializable
class Term(val name: String, val taxonomy: String)

@Serializable
class TermId(val id: Int)
