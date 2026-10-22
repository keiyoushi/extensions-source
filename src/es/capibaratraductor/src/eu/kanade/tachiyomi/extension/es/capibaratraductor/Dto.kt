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
) {
    val data get() = if (status) _data!! else throw Exception(message ?: "Error desconocido")
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
    val chapters: List<SeriesChapterDto>? = emptyList(),
    val organization: OrganizationDto? = null,
    @SerialName("_jointSlug") private val jointSlug: String? = null,
    @SerialName("_jointMembers") private val jointMembers: List<OrganizationDto>? = emptyList(),
    val members: List<MemberDto>? = emptyList(),
) {
    fun toSManga(orgSlug: String? = null) = SManga.create().apply {
        val organizationSlug = orgSlug ?: organization!!.slug
        title = this@SeriesDto.title
        thumbnail_url = imageUrl
        url = "${manga.slug}/$organizationSlug"
        artist = if (jointSlug == null) organization?.name else jointMembers?.joinToString { it.name }
        memo = buildJsonObject {
            jointSlug?.let { put("jointSlug", it) }
        }
    }

    fun toSMangaDetails(orgSlug: String) = toSManga(orgSlug).apply {
        status = parseStatus(this@SeriesDto.status)
        description = this@SeriesDto.description
        title = this@SeriesDto.title
        author = manga.authors?.joinToString { it.name }
        artist = if (!members.isNullOrEmpty()) members.joinToString { it.organization.name } else organization!!.name
    }

    private fun parseStatus(status: String?) = when (status) {
        "ongoing" -> SManga.ONGOING
        "hiatus" -> SManga.ON_HIATUS
        "finished", "completed" -> SManga.COMPLETED
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
    val organization: OrganizationDto,
)

@Serializable
class MangaInfoDto(
    val slug: String,
    val authors: List<SeriesAuthorDto>? = emptyList(),
)

@Serializable
class SeriesAuthorDto(
    val name: String,
)

@Serializable
class SeriesChapterDto(
    private val title: String,
    private val number: Float,
    private val releasedAt: String? = null,
    val isUnreleased: Boolean = false,
    private val jointId: Int? = null,
) {
    fun toSChapter(seriesSlug: String, organizationSlug: String) = SChapter.create().apply {
        name = "Capítulo ${number.toString().removeSuffix(".0")} - $title"
        date_upload = Instant.tryParse(releasedAt)
        url = "$number/$seriesSlug/$organizationSlug"
        memo = buildJsonObject {
            jointId?.let { put("jointId", it) }
        }
    }
}

@Serializable
class PageDto(
    val imageUrl: String,
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
