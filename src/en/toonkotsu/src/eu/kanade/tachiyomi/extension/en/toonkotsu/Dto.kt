package eu.kanade.tachiyomi.extension.en.toonkotsu

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
class BrowseDto(
    val items: List<SeriesDto>,
    val hasNextPage: Boolean,
)

@Serializable
class SeriesDto(
    private val hid: String,
    private val title: String,
    private val cover: String? = null,
    private val type: String? = null,
    private val status: String? = null,
    private val tags: List<String> = emptyList(),
    private val authors: List<String> = emptyList(),
    private val description: String? = null,
    private val alt: List<String> = emptyList(),
    private val externalIds: ExternalIdsDto? = null,
    private val mangadex: String? = null,
    private val score: Int? = null,
    private val year: Int? = null,
    private val magazines: List<MagazineDto> = emptyList(),
    private val muStatus: String? = null,
    private val adaptation: AdaptationDto? = null,
    private val editions: List<EditionDto> = emptyList(),
    private val spoilerTags: List<String> = emptyList(),
) {
    fun toSManga(baseUrl: String, showSpoilerTags: Boolean) = SManga.create().apply {
        url = "/title/$hid"
        title = this@SeriesDto.title
        thumbnail_url = cover?.let { baseUrl + it }
        author = authors.joinToString().ifEmpty { null }
        genre = (listOfNotNull(type) + if (showSpoilerTags) tags else tags - spoilerTags.toSet()).joinToString().ifEmpty { null }
        status = when (this@SeriesDto.status) {
            "Ongoing" -> SManga.ONGOING
            "Complete" -> SManga.COMPLETED
            "Hiatus" -> SManga.ON_HIATUS
            "Canceled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        val info = listOfNotNull(
            score?.let { (it / 20.0).roundToInt().let { stars -> "★".repeat(stars) + "☆".repeat(5 - stars) } + " $it" },
            year?.toString(),
            // MangaUpdates' status, without its per-season breakdown
            muStatus?.let { it.take(it.indexOf(')') + 1).ifEmpty { it } },
            // Adaptations with `via` belong to a related series
            adaptation?.takeIf { it.via == null }?.let {
                "[Anime](https://anilist.co/anime/${it.id}) (${listOfNotNull(it.format?.replace('_', ' '), it.year).joinToString()})"
            },
        )
        val links = listOfNotNull(
            externalIds?.anilist?.let { "[AniList](https://anilist.co/manga/$it)" },
            externalIds?.myanimelist?.let { "[MyAnimeList](https://myanimelist.net/manga/$it)" },
            externalIds?.mangaupdates?.let { "[MangaUpdates](https://www.mangaupdates.com/series/$it)" },
            externalIds?.mangabaka?.let { "[MangaBaka](https://mangabaka.org/$it)" },
            mangadex?.let { "[MangaDex](https://mangadex.org/title/$it)" },
        )
        val extra = listOfNotNull(
            magazines.joinToString { it.name }.ifEmpty { null }?.let { "**Magazines:** $it" },
            alt.joinToString(" · ").ifEmpty { null }?.let { "**Alt titles:** $it" },
            editions.joinToString(" · ") { it.toLink(baseUrl) }.ifEmpty { null }?.let { "**Editions:** $it" },
            links.joinToString(" · ").ifEmpty { null }?.let { "**Links:** $it" },
        )
        description = listOfNotNull(
            info.joinToString(" · ").ifEmpty { null },
            this@SeriesDto.description,
            extra.joinToString("\n").ifEmpty { null },
        ).joinToString("\n\n").ifEmpty { null }
    }
}

@Serializable
class MagazineDto(val name: String)

@Serializable
class AdaptationDto(val id: Long, val format: String? = null, val year: Int? = null, val via: Long? = null)

@Serializable
class EditionDto(private val hid: String, private val title: String) {
    fun toLink(baseUrl: String) = "[${title.replace("[", "\\[").replace("]", "\\]")}]($baseUrl/title/$hid)"
}

@Serializable
class ExternalIdsDto(
    val anilist: String? = null,
    val myanimelist: String? = null,
    val mangaupdates: String? = null,
    val mangabaka: String? = null,
)

@Serializable
class SeriesResponse(
    val series: SeriesDto,
    val chapters: List<ChapterDto>,
    val recs: List<SeriesDto> = emptyList(),
)

@Serializable
class ChapterDto(
    val id: Long,
    val n: Double?,
    private val label: String,
    private val at: Long? = null,
    private val group: GroupDto? = null,
    val partial: Boolean = false,
    private val alts: List<AltChapterDto> = emptyList(),
) {
    val isOfficial get() = group?.slug == "official"

    fun withAlts() = listOf(this) + alts.sortedBy { it.id }.map {
        ChapterDto(it.id, n, it.label ?: label, it.at ?: at, it.group, it.partial)
    }

    fun toSChapter(mangaUrl: String) = SChapter.create().apply {
        url = "$mangaUrl/$id"
        name = label
        date_upload = at ?: 0L
        scanlator = group?.name
    }
}

@Serializable
class AltChapterDto(
    val id: Long,
    val label: String? = null,
    val at: Long? = null,
    val group: GroupDto? = null,
    val partial: Boolean = false,
)

@Serializable
class GroupDto(val name: String, val slug: String? = null)

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
class FacetDto(val name: String)
