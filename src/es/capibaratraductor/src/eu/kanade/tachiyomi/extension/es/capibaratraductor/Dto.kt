package eu.kanade.tachiyomi.extension.es.capibaratraductor

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class Data<T>(
    @SerialName("data") private val _data: T? = null,
    private val status: Boolean = true,
    private val message: String? = null,
    val rv: String? = null,
) {
    val data get() = if (status) _data!! else throw Exception(message ?: "Unknown error")
}

@Serializable
class SeriesListDataDto(
    @SerialName("items") val series: List<SeriesDto> = emptyList(),
    val maxPage: Int = 0,
)

@Serializable
class SeriesDto(
    val manga: MangaInfoDto,
    private val imageUrl: String? = null,
    private val title: String,
    private val status: String? = null,
    private val description: String? = null,
    private val alternativeTitle: String? = null,
    private val genres: List<NameDto>? = null,
    private val mal: MalDto? = null,
    private val malAnimeUrl: String? = null,
    private val hasAnime: Boolean = false,
    private val isOneShot: Boolean = false,
    val chapterLabelMode: String? = null,
    val usersAlsoReadMangaCustomIds: String? = null,
    val chapters: List<SeriesChapterDto>? = emptyList(),
    val organization: OrganizationDto? = null,
    @SerialName("_jointSlug") private val listJointSlug: String? = null,
    private val jointSlug: String? = null,
    @SerialName("_jointMembers") private val jointMembers: List<OrganizationDto>? = emptyList(),
    val members: List<MemberDto>? = emptyList(),
) {
    fun toSManga(orgSlug: String? = null) = SManga.create().apply {
        val organizationSlug = orgSlug ?: organization!!.slug
        title = this@SeriesDto.title
        thumbnail_url = imageUrl
        url = "${manga.slug}/$organizationSlug"
        artist = if (listJointSlug == null) organization?.name else jointMembers?.joinToString { it.name }
        memo = buildJsonObject {
            (listJointSlug ?: jointSlug)?.let { put("jointSlug", it) }
        }
    }

    fun toSMangaDetails(orgSlug: String) = toSManga(orgSlug).apply {
        status = parseStatus(this@SeriesDto.status)
        description = buildString {
            listOfNotNull(
                mal?.score?.let { "★ $it (MAL)" },
                "Oneshot".takeIf { isOneShot },
                "Anime".takeIf { hasAnime },
            ).also { if (it.isNotEmpty()) append(it.joinToString(" · "), "\n\n") }

            this@SeriesDto.description?.let { append(it.trim(), "\n\n") }

            alternativeTitle?.takeIf { it.isNotBlank() }?.let {
                append("**Título alternativo:**\n- ", it.trim(), "\n\n")
            }

            listOfNotNull(
                manga.anilistId?.let { "[AniList](https://anilist.co/manga/$it)" },
                manga.malMangaId?.let { "[MyAnimeList](https://myanimelist.net/manga/$it)" },
                malAnimeUrl?.let { "[MyAnimeList (anime)]($it)" },
            ).also { links ->
                if (links.isNotEmpty()) append("**Enlaces:**\n", links.joinToString("\n") { "- $it" })
            }
        }.trim().ifEmpty { null }
        genre = (listOfNotNull(manga.bookType, manga.demography) + genres.orEmpty())
            .joinToString { it.name }
            .ifEmpty { null }
        author = manga.authors?.joinToString { it.name }
        artist = if (!members.isNullOrEmpty()) members.joinToString { it.organization.name } else organization!!.name
    }

    fun leaderSlug() = members!!.first { it.role == "LEADER" }.organization.slug

    private fun parseStatus(status: String?) = when (status) {
        "ongoing" -> SManga.ONGOING
        "hiatus" -> SManga.ON_HIATUS
        "finished", "completed" -> SManga.COMPLETED
        "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class OrganizationDto(
    val name: String,
    val slug: String,
)

@Serializable
class MemberDto(
    val role: String,
    val organization: OrganizationDto,
)

@Serializable
class MangaInfoDto(
    val slug: String,
    val authors: List<SeriesAuthorDto>? = emptyList(),
    val anilistId: Int? = null,
    val malMangaId: Int? = null,
    val bookType: NameDto? = null,
    val demography: NameDto? = null,
)

@Serializable
class MalDto(
    val score: Float? = null,
)

@Serializable
class NameDto(
    val name: String,
)

@Serializable
class SeriesAuthorDto(
    val name: String,
)

@Serializable
class SeriesChapterDto(
    private val title: String,
    private val number: Float,
    private val displayNumber: Float? = null,
    private val volumeNumber: Float? = null,
    private val releasedAt: String? = null,
    val isUnreleased: Boolean = false,
    private val jointId: Int? = null,
) {
    fun toSChapter(seriesSlug: String, organizationSlug: String, labelMode: String?, jointSlug: String?) = SChapter.create().apply {
        val shownNumber = displayNumber ?: number
        val chapterLabel = "Capítulo ${shownNumber.toString().removeSuffix(".0")}"
        val label = when {
            labelMode == "volume" -> "Tomo ${(volumeNumber ?: shownNumber).toString().removeSuffix(".0")}"
            labelMode == "both" && volumeNumber != null -> "Tomo ${volumeNumber.toString().removeSuffix(".0")} · $chapterLabel"
            else -> chapterLabel
        }
        val trimmedTitle = title.trim()
        val titleNumber = CHAPTER_TITLE_REGEX.matchEntire(trimmedTitle)?.groupValues?.get(1)?.toFloatOrNull()
        name = if (trimmedTitle.isEmpty() || titleNumber == shownNumber || trimmedTitle.equals(label, ignoreCase = true)) label else "$label - $trimmedTitle"
        chapter_number = shownNumber
        date_upload = Instant.tryParse(releasedAt)
        url = "$number/$seriesSlug/$organizationSlug"
        memo = buildJsonObject {
            jointId?.let {
                put("jointId", it)
                jointSlug?.let { put("jointSlug", it) }
            }
        }
    }
}

@Serializable
class PageDto(
    val pg: String,
)

@Serializable
class FilterOptionDto(
    val name: String,
    val slug: String,
)

@Serializable
class FilterData(
    val scans: List<Pair<String, String>>,
    val genres: List<Pair<String, String>>,
    val demographies: List<Pair<String, String>>,
)

@Serializable
class ScanListDto(
    val items: List<ScanDto> = emptyList(),
    private val page: Int,
    private val maxPage: Int,
) {
    fun hasNextPage() = page < maxPage
}

@Serializable
class ScanDto(
    val id: String,
    val name: String,
)

private val CHAPTER_TITLE_REGEX = Regex("""cap[ií]tulo\s+([\d.]+)""", RegexOption.IGNORE_CASE)
