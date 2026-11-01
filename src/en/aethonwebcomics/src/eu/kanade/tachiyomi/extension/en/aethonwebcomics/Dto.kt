package eu.kanade.tachiyomi.extension.en.aethonwebcomics

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import kotlin.time.Instant

@Serializable
class Series(
    private val slug: String,
    private val title: Rendered,
    val meta: SeriesMeta,
    @SerialName("_embedded") private val embedded: Embedded,
) {
    fun toSManga(cover: String?) = SManga.create().apply {
        url = slug
        title = Parser.unescapeEntities(this@Series.title.rendered, false)
        author = embedded.terms("webcomic_writer")
        artist = embedded.terms("webcomic_artist")
        genre = embedded.terms("webcomic_genre")
        description = meta.description()
        status = when (meta.status) {
            "ongoing" -> SManga.ONGOING
            "hiatus", "between_seasons" -> SManga.ON_HIATUS
            "complete" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = cover
        initialized = true
    }
}

@Serializable
class Rendered(val rendered: String)

@Serializable
class SeriesMeta(
    @SerialName("webcomic_status") val status: String,
    @SerialName("webcomic_series_card_image_id") val cardImageId: Int,
    @SerialName("webcomic_series_description") private val synopsis: String,
    @SerialName("webcomic_logline") private val logline: String,
    @SerialName("webcomic_age_rating") private val ageRating: String,
    @SerialName("webcomic_update_cadence") private val cadence: String,
    @SerialName("webcomic_update_day") private val day: String,
) {
    fun description(): String? {
        val schedule = listOf(cadence, day).filter { it.isNotEmpty() }.joinToString(" on ") { it.titleCase() }
        val launch = if (status == "coming_soon") "Coming Soon" else ""
        val info = listOf(launch, ageRating.titleCase(), schedule).filter { it.isNotEmpty() }.joinToString(" · ")
        val paragraphs = Jsoup.parseBodyFragment(synopsis).select("p").map { it.text() }
        val tagline = logline.takeUnless { it in paragraphs }.orEmpty()

        return (listOf(info, tagline) + paragraphs).filter { it.isNotEmpty() }.joinToString("\n\n").takeIf { it.isNotEmpty() }
    }

    private fun String.titleCase() = split('_').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }
}

@Serializable
class Embedded(
    @SerialName("wp:term") private val terms: List<List<Term>>,
) {
    fun terms(taxonomy: String): String? = terms.flatten()
        .filter { it.taxonomy == taxonomy }
        .joinToString { it.name }
        .takeIf { it.isNotEmpty() }
}

@Serializable
class Term(
    val taxonomy: String,
    val name: String,
)

@Serializable
class Media(
    val id: Int,
    @SerialName("source_url") val sourceUrl: String,
)

@Serializable
class Genre(
    val id: Int,
    val name: String,
)

@Serializable
class EpisodeDate(
    val id: Int,
    @SerialName("date_gmt") private val dateGmt: String,
) {
    val date get() = Instant.tryParse("${dateGmt}Z")
}

@Serializable
class EpisodeSeries(val meta: EpisodeMeta)

@Serializable
class EpisodeMeta(
    @SerialName("webcomic_series_id") val seriesId: Int,
)
