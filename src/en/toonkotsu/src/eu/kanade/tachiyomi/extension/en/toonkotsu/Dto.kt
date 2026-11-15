package eu.kanade.tachiyomi.extension.en.toonkotsu

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.math.max
import kotlin.math.min

@Serializable
class BrowseDto(
    val items: List<SeriesDto>,
    val hasNextPage: Boolean,
)

@Serializable
class GroupPageDto(
    val items: List<SeriesDto>,
    private val page: Int,
    private val pages: Int,
) {
    val hasNextPage get() = page < pages
}

@Serializable
class LatestDto(
    val items: List<LatestItemDto>,
    val hasNextPage: Boolean,
)

@Serializable
class LatestItemDto(val series: SeriesDto)

@Serializable
class SeriesDto(
    val id: Long,
    val hid: String,
    private val title: String,
    private val cover: String? = null,
    private val type: String? = null,
    private val originalLanguage: String? = null,
    private val status: String? = null,
    private val tags: List<String> = emptyList(),
    private val spoilerTags: List<String> = emptyList(),
    private val authors: List<String> = emptyList(),
    private val description: String? = null,
    private val titles: TitlesDto? = null,
    private val alt: List<String> = emptyList(),
    private val externalIds: ExternalIdsDto? = null,
    private val mangadex: String? = null,
    private val score: Int? = null,
    private val year: Int? = null,
    private val muStatus: String? = null,
    private val magazines: List<MagazineDto> = emptyList(),
    private val adaptation: AdaptationDto? = null,
    private val editionOf: EditionDto? = null,
    private val editions: List<EditionDto> = emptyList(),
) {
    val isEnglishOriginal get() = originalLanguage == "en"

    fun toSManga(baseUrl: String, showSpoilerTags: Boolean) = SManga.create().apply {
        url = "/title/$hid"
        title = this@SeriesDto.title
        // The size the site's own title page shows
        thumbnail_url = cover?.let { "$baseUrl$it".toHttpUrl() }?.let { it.newBuilder().setPathSegment(it.pathSize - 1, "720.webp").build().toString() }
        author = authors.joinToString().ifEmpty { null }
        val shownTags = if (showSpoilerTags) tags else tags - spoilerTags.toSet()
        genre = (listOfNotNull(type, shownTags.firstOrNull(DEMOGRAPHICS::contains)) + shownTags).distinct().joinToString().ifEmpty { null }
        status = when (this@SeriesDto.status) {
            "Ongoing" -> SManga.ONGOING
            "Complete" -> SManga.COMPLETED
            "Hiatus" -> SManga.ON_HIATUS
            "Canceled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    fun toSMangaDetails(baseUrl: String, showSpoilerTags: Boolean, showAdult: Boolean, mangabaka: MangabakaDto) = toSManga(baseUrl, showSpoilerTags).apply {
        val sources = mangabaka.sources
        description = MarkdownDescription.Builder().apply {
            rating(score?.div(10.0))
            info(
                (year ?: mangabaka.year)?.toString(),
                (mangabaka.finalVolume ?: muStatus?.let { VOLUMES_REGEX.find(it)?.groupValues?.get(1) })?.let { "$it volumes" },
                // The site counts chapters only once a series has ended
                mangabaka.totalChapters?.takeIf { mangabaka.status == "completed" || mangabaka.status == "cancelled" }?.let { "$it chapters" },
            )
            markdown(this@SeriesDto.description?.takeIf { it.isNotBlank() } ?: mangabaka.description)
            field("Magazines", magazines.map { it.name })
            links(listOfNotNull(editionOf).map { it.toLink(baseUrl) }, label = "Edition of")
            links(editions.filter { showAdult || !it.adult }.map { it.toLink(baseUrl) }, label = "Editions")
            // In the order of the site's External Links
            links(
                listOf(
                    "AniList" to (externalIds?.anilist ?: sources?.anilist?.id)?.let { "https://anilist.co/manga/$it" },
                    "MyAnimeList" to (externalIds?.myanimelist ?: sources?.myAnimeList?.id)?.let { "https://myanimelist.net/manga/$it" },
                    "MangaDex" to mangadex?.let { "https://mangadex.org/title/$it" },
                    "MangaUpdates" to (externalIds?.mangaupdates ?: sources?.mangaUpdates?.id)?.let {
                        // The site links numeric (old) MangaUpdates ids to the old page
                        val page = if (it.all(Char::isDigit)) "series.html?id=$it" else "series/$it"
                        "https://www.mangaupdates.com/$page"
                    },
                    "Kitsu" to sources?.kitsu?.id?.let { "https://kitsu.app/manga/$it" },
                    "MangaBaka" to externalIds?.mangabaka?.let { "https://mangabaka.org/$it" },
                    "Anime-Planet" to sources?.animePlanet?.id?.let { "https://www.anime-planet.com/manga/$it" },
                    "Shikimori" to sources?.shikimori?.id?.let { "https://shikimori.io/mangas/$it" },
                    "Anime News Network" to sources?.animeNewsNetwork?.id?.let { "https://www.animenewsnetwork.com/encyclopedia/manga.php?id=$it" },
                ) + listOfNotNull(
                    // Adaptations with `via` belong to a related series
                    adaptation?.takeIf { it.via == null }?.let {
                        val about = listOfNotNull(it.format?.replace('_', ' '), it.year).joinToString().ifEmpty { null }
                        listOfNotNull("Anime", about?.let { "($it)" }).joinToString(" ") to "https://anilist.co/anime/${it.id}"
                    },
                ),
            )
            altTitles(
                (
                    listOfNotNull(titles?.native, mangabaka.nativeTitle, titles?.romaji, titles?.english) +
                        alt + mangabaka.titles.map { it.title }
                    ).filter { it.trim() != title },
            )
        }.build()
    }
}

@Serializable
class TitlesDto(
    val english: String? = null,
    val romaji: String? = null,
    val native: String? = null,
)

@Serializable
class MagazineDto(val name: String)

@Serializable
class AdaptationDto(val id: Long, val format: String? = null, val year: Int? = null, val via: Long? = null)

@Serializable
class EditionDto(private val hid: String, private val title: String, val adult: Boolean = false) {
    fun toLink(baseUrl: String) = title to "$baseUrl/title/$hid"
}

@Serializable
class ExternalIdsDto(
    val anilist: String? = null,
    val myanimelist: String? = null,
    val mangaupdates: String? = null,
    val mangabaka: String? = null,
)

@Serializable
class MangabakaDto(
    val nativeTitle: String? = null,
    val titles: List<MangabakaTitleDto> = emptyList(),
    val description: String? = null,
    val year: Int? = null,
    val status: String? = null,
    val finalVolume: String? = null,
    val totalChapters: String? = null,
    val sources: MangabakaSourcesDto? = null,
)

@Serializable
class MangabakaTitleDto(val title: String)

@Serializable
class MangabakaSourcesDto(
    val anilist: MangabakaSourceDto? = null,
    @SerialName("my_anime_list") val myAnimeList: MangabakaSourceDto? = null,
    @SerialName("manga_updates") val mangaUpdates: MangabakaSourceDto? = null,
    val kitsu: MangabakaSourceDto? = null,
    @SerialName("anime_planet") val animePlanet: MangabakaSourceDto? = null,
    val shikimori: MangabakaSourceDto? = null,
    @SerialName("anime_news_network") val animeNewsNetwork: MangabakaSourceDto? = null,
)

@Serializable
class MangabakaSourceDto(val id: String)

@Serializable
class SeriesResponse(
    val series: SeriesDto,
    val chapters: List<ChapterDto>,
    val recs: List<SeriesDto> = emptyList(),
)

@Serializable
class CoversDto(private val covers: List<CoverDto>) {
    fun earliestEnglishUrl(baseUrl: String) = covers
        .filter { it.locale == "en" }
        .minByOrNull { it.volume?.toFloatOrNull() ?: Float.MAX_VALUE }
        ?.let { "$baseUrl${it.full}" }
}

@Serializable
class CoverDto(val volume: String? = null, val locale: String, val full: String)

@Serializable
class ChapterDto(
    val id: Long,
    val n: Double?,
    private val vol: String? = null,
    private val label: String,
    private val at: Long? = null,
    private val group: GroupDto? = null,
    val partial: Boolean = false,
    private val src: String? = null,
    private val pages: Int? = null,
    private val w: Int? = null,
    private val f: String? = null,
    val mb: Double? = null,
    @Transient val parentId: Long? = null,
    private val alts: List<AltChapterDto> = emptyList(),
) {
    val numericVolume get() = vol?.trim()?.toDoubleOrNull()

    val chapterKey get() = n ?: parentId ?: id

    fun row(byVolume: Boolean, byGroup: Boolean = true) = listOf(
        (numericVolume ?: vol?.trim()?.lowercase().orEmpty()).takeIf { byVolume },
        chapterKey,
        (group?.slug ?: group?.name).takeIf { byGroup },
    )

    fun withAlts() = listOf(this) + alts.sortedBy { it.id }.map {
        ChapterDto(
            id = it.id,
            n = n,
            vol = vol,
            label = it.label ?: label,
            at = it.at ?: at,
            group = it.group,
            partial = it.partial,
            src = it.src,
            pages = it.pages,
            w = it.w,
            f = it.f,
            mb = it.mb,
            parentId = id,
        )
    }

    // The site's version picker shows these as one option
    fun isCopyOf(other: ChapterDto) = group?.name != null && group.name == other.group?.name && partial == other.partial &&
        pages == other.pages && w == other.w && f == other.f &&
        (mb == null || other.mb == null || max(mb, other.mb) <= 1.15 * min(mb, other.mb))

    fun isFromSource(key: String) = key.equals(src, ignoreCase = true)

    fun isFromGroup(name: String) = name.equals(group?.name, ignoreCase = true) || name.equals(group?.slug, ignoreCase = true)

    fun toSChapter(mangaUrl: String, withSourceCode: Boolean, nameFormat: String) = SChapter.create().apply {
        url = "$mangaUrl/$id"
        // The site shows the volume as a badge next to the label. Unnumbered chapters keep the label, or the app reads the volume as their number
        val volume = vol?.trim()?.takeIf { n != null && it.isNotEmpty() && nameFormat != NAME_LABEL && !VOLUME_REGEX.containsMatchIn(label) }?.let { "Vol.$it" }
        val number = n?.toString()?.removeSuffix(".0")?.takeIf { nameFormat == NAME_VOLUME_NUMBER && NUMBER_REGEX.findAll(label).none { match -> match.value == it } }?.let { "Ch.$it" }
        name = listOfNotNull(volume, number, label).joinToString(" ")
        // The site's own chapter number; names can count per season or per arc, or have none
        n?.let { chapter_number = it.toFloat() }
        date_upload = at ?: 0L
        // The site has no names for its sources, only codes; "incomplete" is the site's word for a partial upload
        val notes = listOfNotNull(src?.takeIf { withSourceCode }, "incomplete".takeIf { partial })
        scanlator = group?.name?.let { name -> if (notes.isEmpty()) name else "$name (${notes.joinToString()})" }
    }
}

@Serializable
class AltChapterDto(
    val id: Long,
    val label: String? = null,
    val at: Long? = null,
    val group: GroupDto? = null,
    val partial: Boolean = false,
    val src: String? = null,
    val pages: Int? = null,
    val w: Int? = null,
    val f: String? = null,
    val mb: Double? = null,
)

@Serializable
class GroupDto(val name: String, val slug: String? = null)

@Serializable
class LikesDto(val counts: Map<String, Int> = emptyMap())

@Serializable
class ChapterResponse(val chapter: ChapterPagesDto)

@Serializable
class ChapterPagesDto(val pages: List<PageDto>)

@Serializable
class PageDto(
    val src: String,
    val k: String,
    val n: String,
)

@Serializable
class LimitDto(
    val error: String? = null,
    val code: String? = null,
    val reason: String? = null,
)

@Serializable
class UnlockBody(private val token: String)

@Serializable
class FacetsDto(
    val genres: List<FacetDto>,
    val types: List<FacetDto>,
)

@Serializable
class FacetDto(
    val name: String,
    val category: String? = null,
    val isAdult: Boolean = false,
)

const val NAME_LABEL = "label"
const val NAME_VOLUME = "volume"
const val NAME_VOLUME_NUMBER = "volume_number"

// Labels that already name their volume ("Volume 30", "Vol. 3 Ch. 12")
private val VOLUME_REGEX = Regex("""\bvol(?:ume)?\.?\s*\d""", RegexOption.IGNORE_CASE)

private val VOLUMES_REGEX = Regex("""^(\d+)\s+Volumes?""", RegexOption.IGNORE_CASE)

private val NUMBER_REGEX = Regex("""\d+(?:\.\d+)?""")
