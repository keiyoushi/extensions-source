package eu.kanade.tachiyomi.extension.en.webtoonlib

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class SeriesList(
    val items: List<Series>,
    val total: Int,
)

@Serializable
class Series(
    val id: String,
    private val slug: String,
    private val title: String,
    private val cover: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val alternativeTitle: String? = null,
    private val description: String? = null,
    private val detailedSummary: String? = null,
    private val detailedSummaryHasSpoiler: Boolean = false,
    private val publisher: String? = null,
    private val rating: Double? = null,
    val isAdult: Boolean = false,
    private val genres: List<String> = emptyList(),
    private val status: String? = null,
    private val type: String? = null,
    private val malId: Long? = null,
    private val mangaBakaId: Long? = null,
    val publisherGroup: PublisherGroup? = null,
) {
    fun toSManga() = SManga.create().apply {
        val summary = detailedSummary.takeUnless { detailedSummaryHasSpoiler } ?: this@Series.description
        url = slug
        title = this@Series.title
        thumbnail_url = cover
        author = this@Series.author
        artist = this@Series.artist
        description = MarkdownDescription.Builder()
            .rating(rating?.takeIf { it > 0 })
            .info(publisher)
            .description(summary?.let { Jsoup.parseBodyFragment(it).body() })
            .altTitles(listOfNotNull(alternativeTitle))
            .links(
                listOf(
                    "MangaBaka" to mangaBakaId?.let { "https://mangabaka.org/$it" },
                    "MyAnimeList" to malId?.let { "https://myanimelist.net/manga/$it" },
                ),
            )
            .build()
        genre = (listOfNotNull(type) + genres + listOfNotNull("Adult".takeIf { isAdult })).distinct().joinToString().ifEmpty { null }
        status = when (this@Series.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class PublisherGroup(val name: String)

@Serializable
class ChapterList(
    val items: List<Chapter>,
    val totalPages: Int,
)

@Serializable
class Chapter(
    private val chapterKey: String,
    private val chapterNo: Float,
    private val label: String,
    private val publisherLabel: String? = null,
    private val episodeTitleEn: String? = null,
    private val releaseDate: String? = null,
    private val translation: Translation? = null,
) {
    fun toSChapter(slug: String, group: String?) = SChapter.create().apply {
        url = "$slug/$chapterKey"
        val number = publisherLabel ?: "Chapter $label"
        name = when {
            episodeTitleEn == null || episodeTitleEn in number -> number
            episodeTitleEn.startsWith(number) -> episodeTitleEn
            else -> "$number: $episodeTitleEn"
        }
        // chapterNo is a running count that drifts from the labels
        chapter_number = CHAPTER_NUMBER.find(number)?.groupValues?.get(1)?.toFloat() ?: chapterNo
        date_upload = Instant.tryParse(releaseDate)
        scanlator = if (translation?.isOfficial == false) "WebtoonLib AI" else group
    }
}

private val CHAPTER_NUMBER = Regex("""Chapter\s+(\d+(?:\.\d+)?)""")

@Serializable
class Translation(val isOfficial: Boolean)

@Serializable
class Reader(
    val chapterType: String,
    val pages: List<String>,
    val manifestUrl: String? = null,
    private val readerAccessRequired: Boolean = false,
) {
    val needsAccess get() = readerAccessRequired && pages.isEmpty() && manifestUrl == null
}

@Serializable
class Manifest(
    val sourceLanguage: String,
    val pages: List<ManifestPage>,
)

@Serializable
class ManifestPage(
    val cleanImageUrl: String,
    val originalImageUrl: String,
    val bubbles: List<Bubble>,
)

@Serializable
class Bubble(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val text: String,
    val fontSize: Float? = null,
    val color: String? = null,
    val textAlign: String? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val lineHeight: Float? = null,
    val letterSpacing: Float? = null,
    val strokeColor: String? = null,
    val strokeWidth: Float? = null,
    val rotation: Float? = null,
)

@Serializable
class SearchAccess(
    val challengeRequired: Boolean,
    val siteKey: String,
)

@Serializable
class ReaderAccess(
    val enabled: Boolean,
    val verified: Boolean,
    val siteKey: String?,
)

@Serializable
class TurnstileVerification(private val token: String)

@Serializable
class FilterData(
    val types: List<FilterOption>,
    val statuses: List<FilterOption>,
    val genres: List<FilterOption>,
)

@Serializable
class FilterOption(
    val name: String,
    val value: String,
)
