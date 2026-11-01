package eu.kanade.tachiyomi.extension.vi.cotruyenday

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.Normalizer
import kotlin.time.Instant

private val nonWord = Regex("[^a-z0-9]+")

internal fun String.toSlug(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .replace('đ', 'd').replace('Đ', 'D')
    .lowercase()
    .replace(nonWord, "-")
    .trim('-')

@Serializable
class BootstrapResponse(
    val data: BootstrapData,
)

@Serializable
class BootstrapData(
    @SerialName("endpoint_api") val endpointApi: String,
)

@Serializable
class ListResponse(
    val data: ListData,
)

@Serializable
class ListData(
    val comics: List<ComicDto> = emptyList(),
    val page: Int = 1,
    val totalPage: Int = 1,
)

@Serializable
class ComicDto(
    val mangaId: Long,
    val mangaName: String,
    val coverImage: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = mangaId.toString()
        title = mangaName
        thumbnail_url = coverImage
        memo = buildJsonObject {
            put("mangaSlug", "${mangaName.toSlug()}-$mangaId")
        }
    }
}

@Serializable
class DetailResponse(
    val data: DetailDto,
)

@Serializable
class DetailDto(
    val mangaId: Long,
    val mangaName: String,
    val description: String? = null,
    val coverImage: String? = null,
    val status: String? = null,
    @SerialName("gender_text") val genreText: String? = null,
    val type: String? = null,
    @SerialName("full_tags") val fullTags: List<TagDto> = emptyList(),
) {
    fun toSManga(url: String): SManga = SManga.create().apply {
        this.url = url
        title = mangaName
        this.description = this@DetailDto.description
        thumbnail_url = coverImage
        genre = (fullTags.map { it.tagName } + listOfNotNull(genreText, type)).joinToString()
        status = when (this@DetailDto.status) {
            "Đang tiến hành" -> SManga.ONGOING
            "Hoàn thành" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        memo = buildJsonObject {
            put("mangaSlug", "${mangaName.toSlug()}-$mangaId")
        }
    }
}

@Serializable
class TagDto(
    @SerialName("tag_name") val tagName: String,
)

@Serializable
class ChapterListResponse(
    val data: ChapterListData,
)

@Serializable
class ChapterListData(
    val comics: List<ChapterDto> = emptyList(),
    val totalPage: Int = 1,
)

@Serializable
class ChapterDto(
    val chapterId: Long,
    val chapterTitle: String? = null,
    val chapterNumber: String? = null,
    val publishedDate: String? = null,
) {
    fun toSChapter(mangaSlug: String): SChapter = SChapter.create().apply {
        url = chapterId.toString()
        name = "Chapter ${chapterNumber ?: chapterTitle ?: chapterId}"
        chapter_number = chapterNumber?.toFloatOrNull() ?: -1f
        date_upload = Instant.tryParse(publishedDate)
        memo = buildJsonObject {
            put("mangaSlug", mangaSlug)
            chapterNumber?.let { put("num", it) }
        }
    }
}

@Serializable
class ChapterResponse(
    val data: ChapterDataDto,
)

@Serializable
class ChapterDataDto(
    @SerialName("image_urls") val imageUrls: List<String> = emptyList(),
    @SerialName("encrypted_images") val encryptedImages: EncryptedDto? = null,
)

@Serializable
class EncryptedDto(
    val iv: String,
    @SerialName("encrypted_data") val encryptedData: String,
)

@Serializable
class GenreListResponse(
    val data: List<GenreDto> = emptyList(),
)

@Serializable
class GenreDto(
    val genreId: Long,
    val genreName: String,
)
