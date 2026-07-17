package eu.kanade.tachiyomi.extension.en.allanime

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class Edges<T>(val edges: List<T>)

@Serializable
class PopularData(
    @SerialName("queryPopular") val popular: PopularMangas,
)

@Serializable
class PopularMangas(
    @SerialName("recommendations") val mangas: List<PopularManga>,
)

@Serializable
class PopularManga(
    @SerialName("anyCard") val manga: SearchManga? = null,
)

@Serializable
class SearchData(
    val mangas: Edges<SearchManga>,
)

@Serializable
class SearchManga(
    @SerialName("_id") val id: String,
    private val name: String,
    private val thumbnail: String? = null,
    private val englishName: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = englishName ?: name
        url = id
        memo = buildJsonObject {
            put("slug", name.titleToSlug())
        }
        thumbnail_url = thumbnail?.parseThumbnailUrl()
    }
}

@Serializable
class MangaUpdateData(
    val manga: Manga,
    @SerialName("episodeInfos") val chapterList: List<ChapterData>,
)

@Serializable
class Manga(
    @SerialName("_id") val id: String,
    private val name: String,
    private val thumbnail: String? = null,
    private val description: String? = null,
    private val authors: List<String>? = emptyList(),
    private val genres: List<String>? = emptyList(),
    private val tags: List<String>? = emptyList(),
    private val status: String? = null,
    private val altNames: List<String>? = emptyList(),
    private val englishName: String? = null,
    private val malId: String? = null,
    private val aniListId: String? = null,
    private val relatedMangas: List<Related>? = null,
    val availableChaptersDetail: AvailableChaptersDetail,
) {
    fun toSManga() = SManga.create().apply {
        title = englishName ?: name
        url = id
        memo = buildJsonObject {
            put("slug", name.titleToSlug())
            relatedMangas?.also { put("relatedMangas", it.toJsonElement()) }
        }
        thumbnail_url = thumbnail?.parseThumbnailUrl()
        description = buildString {
            this@Manga.description?.let { append(Jsoup.parseBodyFragment(it).wholeText()) }
            append("\n\n")
            this@Manga.malId?.let { append("[MyAnimeList](https://myanimelist.net/manga/$it)\n") }
            this@Manga.aniListId?.let { append("[AniList](https://anilist.co/manga/$it)\n") }
            append("\n\n")
            if (altNames.orEmpty().isNotEmpty()) {
                append("Alternative Titles:\n")
                append(altNames.orEmpty().joinToString("\n") { "- $it" })
            }
        }.trim()
        if (authors?.isNotEmpty() == true) {
            author = authors.first().trim()
            artist = author
        }
        genre = ((genres ?: emptyList()) + (tags ?: emptyList()))
            .joinToString { it.trim() }
        status = this@Manga.status.parseStatus()
    }
}

@Serializable
class Related(
    val mangaId: String,
)

@Serializable
class RelatedData(
    @SerialName("mangas") val search: Edges<SearchManga>?,
    val fewerGenresSearch: Edges<SearchManga>?,
    val mangasWithIds: List<SearchManga>?,
)

@Serializable
class AvailableChaptersDetail(
    val sub: List<String>,
)

@Serializable
class ChapterData(
    @SerialName("episodeIdNum") val chapterNum: JsonPrimitive,
    @SerialName("notes") val title: String? = null,
    private val uploadDates: DateDto? = null,
) {
    fun toSChapter(mangaId: String, mangaSlug: String, legacy: Boolean = false) = SChapter.create().apply {
        name = "Chapter $chapterNum"
        if (!title.isNullOrEmpty() && !title.contains(numberRegex)) {
            name += ": $title"
        }
        // TODO: remove this path after a while, kept for downloaded chapters compatibility
        url = if (legacy) {
            "/read/$mangaId/$mangaSlug/chapter-$chapterNum-sub"
        } else {
            chapterNum.toString()
        }
        memo = buildJsonObject {
            put("mangaId", mangaId)
            put("mangaSlug", mangaSlug)
        }
        date_upload = uploadDates?.sub?.let { Instant.parseOrNull(it)?.toEpochMilliseconds() } ?: 0L
    }
}

private val numberRegex = Regex("\\d")

@Serializable
class DateDto(
    val sub: String? = null,
)

@Serializable
class PageListData(
    @SerialName("chapterPages") val pageList: Edges<Servers>?,
)

@Serializable
class Servers(
    @SerialName("pictureUrlHead") val serverUrl: String? = null,
    val pictureUrls: List<PageUrl> = emptyList(),
)

@Serializable
class PageUrl(
    val url: String?,
)
