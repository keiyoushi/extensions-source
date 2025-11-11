package eu.kanade.tachiyomi.extension.zh.creativecomic

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class PopularResponseDto(val data: PopularDto)

@Serializable
class PopularDto(val total: Int, val data: List<MangaDto>)

@Serializable
class MangaDto(
    private val id: Int,
    private val name: String,
    private val image1: String,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = image1
    }
}

@Serializable
class DetailsResponseDto(val data: DetailsDto)

@Serializable
class DetailsDto(
    private val name: String,
    private val description: String,
    private val image1: String,
    private val author: List<AuthorDto>,
    private val type: GenreDto,
    private val tags: List<GenreDto>,
    private val completed: Int,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        thumbnail_url = image1
        author = this@DetailsDto.author.joinToString { it.name }
        description = this@DetailsDto.description
        genre = "${type.name}, ${tags.joinToString{ it.name }}"
        status = if (completed == 1) SManga.COMPLETED else SManga.ONGOING
    }
}

@Serializable
class GenreDto(val name: String)

@Serializable
class AuthorDto(val name: String)

@Serializable
class ChapterListResponseDto(val data: ChapterListDataDto)

@Serializable
class ChapterListDataDto(val chapters: List<ChapterDto>)

@Serializable
class ChapterDto(
    private val id: Int,
    private val name: String,
    @SerialName("vol_name") private val volName: String,
    @SerialName("is_free") private val isFree: Int,
    @SerialName("is_buy") private val isBuy: Int,
    @SerialName("is_rent") private val isRent: Int,
    @SerialName("sales_plan") private val salesPlan: Int,
    @SerialName("online_at") private val onlineAt: String,
) {
    fun toSChapter() = SChapter.create().apply {
        url = id.toString()
        // Prepend lock emoji to name if locked
        val isReadable = isFree == 1 || isBuy == 1 || isRent == 1 || salesPlan == 0
        name = (if (isReadable) "" else "\uD83D\uDD12") + "$volName ${this@ChapterDto.name}"
        date_upload = dateFormat.tryParse(onlineAt)
    }
}

@Serializable
class PageListResponseDto(val data: PageListDataDto)

@Serializable
class PageListDataDto(val chapter: PageListChapterDto)

@Serializable
class PageListChapterDto(val proportion: List<PageDto>)

@Serializable
class PageDto(val id: Int)

@Serializable
class ImageUrlResponseDto(val data: ImageUrlDto)

@Serializable
class ImageUrlDto(val key: String)

private val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.ENGLISH)
}

@Serializable
class JWTClaims(val exp: Int)
