package eu.kanade.tachiyomi.extension.all.mangaup

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.protobuf.ProtoNumber
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class SeriesResponse(
    @ProtoNumber(2) val titles: List<MangaTitle>?,
)

@Serializable
class SearchResponse(
    @ProtoNumber(1) val titles: List<MangaTitle>?,
)

@Serializable
class MyPageResponse(
    @ProtoNumber(1) val favorites: List<MangaTitle>?,
    @ProtoNumber(2) val history: List<MangaTitle>?,
)

@Serializable
class MangaTitle(
    @ProtoNumber(1) private val id: Int,
    @ProtoNumber(2) private val name: String,
    @ProtoNumber(3) private val thumbnail: String?,
    @ProtoNumber(7) val bookmarks: Int,
    @ProtoNumber(9) private val lastUpdated: String,
) {
    val updatedAt: Long
        get() = dateFormat.tryParseDate(lastUpdated)

    fun toSManga(imgUrl: String) = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = imgUrl + thumbnail
    }
}

@Serializable
class MangaDetailResponse(
    @ProtoNumber(3) private val title: String,
    @ProtoNumber(4) private val author: String?,
    @ProtoNumber(5) private val copyright: String?,
    @ProtoNumber(6) private val schedule: String?,
    @ProtoNumber(7) private val warning: String?,
    @ProtoNumber(8) private val description: String?,
    @ProtoNumber(10) private val tags: List<GenreDto>?,
    @ProtoNumber(11) private val thumbnail: String?,
    @ProtoNumber(13) val chapters: List<MangaChapter>,
) {
    fun toSManga(mangaId: String, imgUrl: String) = SManga.create().apply {
        url = mangaId
        title = this@MangaDetailResponse.title
        author = this@MangaDetailResponse.author
        description = buildString {
            this@MangaDetailResponse.description?.takeIf { it.isNotEmpty() }?.let { append(it) }
            copyright?.takeIf { it.isNotEmpty() }?.let { append("\n\n$it") }
            schedule?.takeIf { it.isNotEmpty() }?.let { append("\n\n$it") }
            warning?.takeIf { it.isNotEmpty() }?.let { append("\n\n$it") }
        }
        genre = tags?.joinToString { it.name }
        thumbnail_url = imgUrl + thumbnail
        status = if (chapters.any { it.isFinal == true }) SManga.COMPLETED else SManga.ONGOING
    }
}

@Serializable
class GenreDto(
    @ProtoNumber(2) val name: String,
)

@Serializable
class MangaChapter(
    @ProtoNumber(1) private val id: Int,
    @ProtoNumber(2) private val name: String,
    @ProtoNumber(3) private val subtitle: String?,
    @ProtoNumber(6) val price: Int?,
    @ProtoNumber(9) private val dateStr: String?,
    @ProtoNumber(12) val isFinal: Boolean?,
) {
    fun toSChapter(mangaId: String) = SChapter.create().apply {
        url = id.toString()
        var title = this@MangaChapter.name + (if (subtitle != null) " - $subtitle" else "")
        if (isFinal == true) {
            title += " [Final]"
        }
        name = if (price != null) "🔒 $title" else title
        date_upload = dateFormat.tryParseDate(dateStr)
        memo = buildJsonObject {
            put("titleId", mangaId)
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US).withZone(ZoneId.of("Asia/Tokyo"))

@Serializable
class ViewerResponse(
    @ProtoNumber(3) val pageBlocks: List<PageBlock>,
)

@Serializable
class PageBlock(
    @ProtoNumber(1) val chapterId: Int?,
    @ProtoNumber(3) val pages: List<MangaPage>,
)

@Serializable
class MangaPage(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(5) val key: String?,
    @ProtoNumber(6) val iv: String?,
)
