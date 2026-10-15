package eu.kanade.tachiyomi.extension.fr.banchanscan

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLDecoder
import java.text.Normalizer
import kotlin.time.Instant

// The site derives its `/webtoon/<slug>` URLs from the title on the client - there is no slug
// column in the API, so the extension must reproduce the same normalization to link the two.
fun String.toSlug(): String = foldAccents().replace(NON_ALPHANUMERIC_REGEX, "-").trim('-')

fun String.foldAccents(): String = Normalizer.normalize(this, Normalizer.Form.NFD).replace(DIACRITICS_REGEX, "").lowercase()

private val DIACRITICS_REGEX = Regex("\\p{Mn}+")
private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]+")

@Serializable
class WebtoonDto(
    val id: String,
    val title: String,
    @SerialName("alt_title") private val altTitle: String?,
    private val status: String,
    private val description: String?,
    private val genres: List<String>,
    @SerialName("cover_url") private val coverUrl: String?,
    @SerialName("is_adult") private val isAdult: Boolean,
    private val author: String?,
    private val artist: String?,
    private val demographic: String?,
    @SerialName("updated_at") val updatedAt: String,
) {
    fun matches(normalizedQuery: String, selectedGenres: List<String>): Boolean {
        val titleMatches = normalizedQuery.isEmpty() ||
            title.foldAccents().contains(normalizedQuery) ||
            altTitle?.foldAccents()?.contains(normalizedQuery) == true
        val genreMatches = selectedGenres.isEmpty() || genres.containsAll(selectedGenres)
        return titleMatches && genreMatches
    }

    fun toSManga(): SManga = SManga.create().apply {
        // Kept stable across title changes - see BanchanScan.getMangaUrl(), which rebuilds the
        // real "/webtoon/<slug>" URL from the (always up to date) title instead of storing it here.
        url = id
        this.title = this@WebtoonDto.title
        thumbnail_url = coverUrl
        description = this@WebtoonDto.description
        genre = buildList {
            addAll(genres)
            demographic?.let(::add)
            if (isAdult) add("Adulte")
        }.joinToString()
        author = this@WebtoonDto.author
        artist = this@WebtoonDto.artist
        status = when (this@WebtoonDto.status) {
            "En cours" -> SManga.ONGOING
            "Terminé" -> SManga.COMPLETED
            "En pause" -> SManga.ON_HIATUS
            "Licenciée" -> SManga.LICENSED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterDto(
    private val id: String,
    @SerialName("webtoon_id") val webtoonId: String,
    @SerialName("chapter_number") private val chapterNumber: String,
    private val season: String,
    @SerialName("chapter_title") private val chapterTitle: String?,
    @SerialName("published_at") private val publishedAt: String?,
    @SerialName("created_at") private val createdAt: String,
    @SerialName("uploader_name") private val uploaderName: String?,
) {
    // Used to detect whether a webtoon actually spans more than one season - the site only
    // shows the "Saison N ·" prefix in that case, even when every chapter's season is set.
    val seasonOrNull: String? get() = season.takeIf { it.isNotBlank() }

    // A few series reset or overlap chapter_number between seasons (e.g. season 4 runs up to
    // 137 while season 5 only reaches 133), so sorting by chapter_number alone can rank an
    // earlier season above a later one - season must be the primary sort key.
    val seasonNumber: Int get() = season.toIntOrNull() ?: 0
    val chapterNumberValue: Float get() = chapterNumber.toFloatOrNull() ?: -1f

    fun toSChapter(mangaSlug: String, showSeason: Boolean): SChapter = SChapter.create().apply {
        // Kept stable across chapter renumbering - see BanchanScan.getChapterUrl(), which rebuilds
        // the real "/webtoon/<slug>/chapitre_<n>" URL from the manga slug stashed in memo below.
        url = id
        val meta = parseTitleMetadata(chapterTitle)
        name = if (meta.kind == "side_story" && meta.sideStoryNumber != null) {
            "Side Story ${meta.sideStoryNumber}"
        } else {
            buildString {
                if (showSeason && season.isNotBlank()) append("Saison $season · ")
                append("Chapitre $chapterNumber")
                meta.humanText.takeIf { it.isNotBlank() && it != chapterNumber }?.let { append(" : $it") }
                val badges = listOfNotNull(kindBadge(meta.kind), endingBadge(meta.ending, season))
                if (badges.isNotEmpty()) append(badges.joinToString(separator = " · ", prefix = " · "))
            }
        }
        chapter_number = chapterNumber.toFloatOrNull() ?: -1f
        date_upload = Instant.tryParse(publishedAt ?: createdAt)
        scanlator = uploaderName
        memo = buildJsonObject { put("mangaSlug", mangaSlug) }
    }

    private fun kindBadge(kind: String?): String? = when (kind) {
        "faq" -> "FAQ"
        "bonus" -> "Chapitre bonus"
        else -> null
    }

    private fun endingBadge(ending: String?, season: String): String? = when (ending) {
        "main_story" -> "Fin de l'histoire principale"
        "full_story" -> "Fin de l'histoire"
        "season_end" -> if (season.isNotBlank()) "Fin de la saison $season" else "Fin de la saison"
        else -> null
    }

    // chapter_title sometimes carries an invisible-separator-delimited "banchan:<url-encoded json>"
    // suffix the site uses to flag side stories, FAQs, bonus chapters and story/season finales -
    // strip and decode it instead of showing it as raw text.
    private fun parseTitleMetadata(raw: String?): TitleMetadata {
        val cleaned = raw.orEmpty().replace(INVISIBLE_SEPARATOR, "")
        val markerIndex = cleaned.indexOf(METADATA_MARKER)
        if (markerIndex < 0) return TitleMetadata(cleaned, null, null, null)

        val humanText = cleaned.substring(0, markerIndex)
        val payload = runCatching {
            URLDecoder.decode(cleaned.substring(markerIndex + METADATA_MARKER.length), "UTF-8")
                .parseAs<ChapterMetadataDto>()
        }.getOrNull()

        return TitleMetadata(humanText, payload?.kind, payload?.sideStoryNumber, payload?.ending)
    }

    private class TitleMetadata(val humanText: String, val kind: String?, val sideStoryNumber: Int?, val ending: String?)

    companion object {
        private const val INVISIBLE_SEPARATOR = "⁣"
        private const val METADATA_MARKER = "banchan:"
    }
}

@Serializable
class ChapterMetadataDto(val kind: String, val sideStoryNumber: Int? = null, val ending: String? = null)

@Serializable
class ChapterPageDto(
    @SerialName("image_url") val imageUrl: String,
    @SerialName("sort_order") val sortOrder: Int,
)

@Serializable
class GenreDto(val name: String)

@Serializable
class ViewCountDto(
    @SerialName("webtoon_id") val webtoonId: String,
    @SerialName("view_count") private val viewCount: String,
) {
    fun toIntOrZero(): Int = viewCount.toIntOrNull() ?: 0
}

@Serializable
class HomeDataDto(
    val webtoons: List<WebtoonDto>,
    @SerialName("dailyTopViews") val dailyTopViews: List<ViewCountDto>,
    @SerialName("publishedWebtoonIds") val publishedWebtoonIds: List<String>,
)
