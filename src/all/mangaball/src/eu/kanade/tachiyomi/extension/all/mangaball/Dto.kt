package eu.kanade.tachiyomi.extension.all.mangaball

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
class SearchResponse(
    val data: List<MangaDto>,
    private val pagination: Pagination,
) {
    @Serializable
    class Pagination(
        private val page: Int,
        @SerialName("total_pages")
        private val totalPages: Int,
    ) {
        fun hasNextPage() = page < totalPages
    }

    fun toMangasPage() = MangasPage(data.map { it.toSManga() }, pagination.hasNextPage())
}

@Serializable
class MangaDto(
    private val id: String,
    private val name: String,
    private val image: ImageDto? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = id
        title = name
        thumbnail_url = image?.url
    }
}

@Serializable
class TitleResponse(
    val data: TitleDto,
)

@Serializable
class TitleDto(
    private val id: String,
    private val name: String,
    private val image: ImageDto? = null,
    private val description: List<String> = emptyList(),
    private val alternateName: List<String> = emptyList(),
    private val tags: List<TagDto> = emptyList(),
    private val author: List<AuthorDto> = emptyList(),
    private val status: String? = null,
) {
    fun toSManga(): SManga {
        val altNames = alternateName.joinToString("\n") { "- $it" }
        val description = buildString {
            append(this@TitleDto.description.joinToString("\n\n"))
            if (altNames.isNotBlank()) {
                append("\n\nAlternative Names: \n", altNames)
            }
        }.trim()

        return SManga.create().apply {
            url = id
            title = name
            thumbnail_url = image?.url
            genre = tags.joinToString { it.name }
            author = this@TitleDto.author.joinToString { it.name }
            this.description = description
            this.status = when (this@TitleDto.status) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "cancelled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }
}

@Serializable
class ImageDto(
    private val file: String? = null,
    @SerialName("cdn_mangadex")
    private val cdnMangadex: String? = null,
    @SerialName("cdn_mangaupdate")
    private val cdnMangaupdate: String? = null,
    @SerialName("cdn_mangaupdates")
    private val cdnMangaupdates: String? = null,
    private val cover: CoverDto? = null,
) {
    // Mirrors the site's getTitleImage priority: the self-hosted cover first, external mirrors only as fallbacks.
    val url: String?
        get() = cover?.url
            ?: listOf(file, cdnMangadex, cdnMangaupdate, cdnMangaupdates)
                .firstOrNull { !it.isNullOrBlank() }
}

@Serializable
class CoverDto(
    private val path: String? = null,
) {
    // `path` is a Windows-style relative path such as "<titleId>\\cover_123.jpg".
    val url: String?
        get() = path?.trim()
            ?.replace('\\', '/')
            ?.takeIf { it.isNotEmpty() }
            ?.let { if (it.startsWith("http")) it else COVER_BASE_URL + it }
}

@Serializable
class TagDto(
    val name: String,
)

@Serializable
class AuthorDto(
    val name: String,
)

@Serializable
class ChapterListResponse(
    val data: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    private val id: String,
    private val name: String? = null,
    private val number: Float,
    private val volume: Float = 0f,
    private val lang: String,
    private val group: GroupDto? = null,
    @SerialName("created_at")
    private val createdAt: String? = null,
) {
    fun toSChapter(langs: List<String>): SChapter? {
        if (lang !in langs) return null

        val chapterName = name.orEmpty().trim()

        return SChapter.create().apply {
            url = id
            name = buildString {
                if (volume > 0) {
                    append("Vol. ", volume.toString().removeSuffix(".0"), " ")
                }
                val numberStr = number.toString().removeSuffix(".0")
                if (chapterName.contains(numberStr)) {
                    append(chapterName)
                } else {
                    append("Ch. ", numberStr)
                    if (chapterName.isNotEmpty()) {
                        append(" ", chapterName)
                    }
                }
            }
            chapter_number = number
            date_upload = dateFormat.tryParseDateTime(createdAt, ZoneOffset.UTC)
            scanlator = group?.name
        }
    }
}

private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE_TIME

private const val COVER_BASE_URL = "https://bulbasaur.poke-black-and-white.net/covers/"

@Serializable
class GroupDto(
    val name: String,
)

@Serializable
class ChapterDetailResponse(
    val data: ChapterDetailData,
) {
    @Serializable
    class ChapterDetailData(
        val chapter: ChapterDetailDto,
    )
}

@Serializable
class ChapterDetailDto(
    @SerialName("title_id")
    val titleId: String? = null,
    val pages: List<String> = emptyList(),
)

@Serializable
class TitleIdRequest(
    @SerialName("title_id")
    private val titleId: String,
)

@Serializable
class ViewRequest(
    @SerialName("object_id")
    private val objectId: String,
    @SerialName("object_type")
    private val objectType: String,
)
