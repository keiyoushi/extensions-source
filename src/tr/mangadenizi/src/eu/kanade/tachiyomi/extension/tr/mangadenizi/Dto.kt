package eu.kanade.tachiyomi.extension.tr.mangadenizi

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat

@Serializable
class InertiaDto<T>(
    val props: T,
)

@Serializable
class MangaIndexDto(
    val manga: MangaListDataDto,
)

@Serializable
class MangaListDataDto(
    val data: List<MangaDto>,
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
class MangaDetailsDto(
    val manga: MangaDto,
)

@Serializable
class MangaDto(
    private val title: String,
    val slug: String,
    @SerialName("cover_url") private val coverUrl: String,
    private val description: String? = null,
    private val status: String? = null,
    private val categories: List<CategoryDto> = emptyList(),
    private val authors: List<AuthorDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        url = "/manga/$slug"
        thumbnail_url = coverUrl
        description = this@MangaDto.description
        status = when (this@MangaDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = categories.joinToString { it.name }
        author = authors.joinToString { it.name }
    }
}

@Serializable
class CategoryDto(
    val name: String,
)

@Serializable
class AuthorDto(
    val name: String,
)

@Serializable
class ChapterDto(
    private val number: JsonElement,
    private val title: String? = null,
    private val slug: String,
    @SerialName("published_at") private val publishedAt: String,
) {
    fun toSChapter(mangaSlug: String, dateFormat: SimpleDateFormat) = SChapter.create().apply {
        url = "/read/$mangaSlug/$slug"
        val numberStr = number.jsonPrimitive.contentOrNull ?: number.toString()
        name = "Bölüm $numberStr" + (if (title.isNullOrBlank()) "" else ": $title")
        date_upload = dateFormat.tryParse(publishedAt)
    }
}

@Serializable
class ReaderDto(
    val pages: List<ReaderPageDto>,
)

@Serializable
class ReaderPageDto(
    @SerialName("image_url") private val url: String,
) {
    fun toPage(index: Int) = Page(index, imageUrl = url)
}
