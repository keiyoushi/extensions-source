package eu.kanade.tachiyomi.extension.all.mangaup

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class PopularResponse(
    @ProtoNumber(2) val titles: List<MangaTitle>?,
)

@Serializable
class SearchResponse(
    @ProtoNumber(1) val titles: List<MangaTitle>?,
)

@Serializable
class HomeResponse(
    @ProtoNumber(6) val type: String,
    @ProtoNumber(7) val updates: List<MangaTitle>?,
    @ProtoNumber(11) val newSeries: List<MangaTitle>?,
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
) {
    fun toSManga(imgUrl: String) = SManga.create().apply {
        url = "/manga/$id"
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
        url = "/manga/$mangaId"
        title = this@MangaDetailResponse.title
        author = this@MangaDetailResponse.author
        description = buildString {
            this@MangaDetailResponse.description?.let {
                append(it)
                append("\n\n")
            }
            copyright?.let {
                append(it)
                append("\n\n")
            }
            schedule?.let {
                append(it)
                append("\n\n")
            }
            warning?.let { append(it) }
        }.trim()
        genre = tags?.joinToString { it.name }
        thumbnail_url = imgUrl + thumbnail
        status = if (chapters.any { it.status == 1 }) SManga.COMPLETED else SManga.ONGOING
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
    @ProtoNumber(12) val status: Int?,
) {
    fun toSChapter(mangaId: String) = SChapter.create().apply {
        url = "/manga/$mangaId/$id"
        var title = this@MangaChapter.name + (if (subtitle != null) " - $subtitle" else "")
        if (status == 1) {
            title += " [Final]"
        }
        name = if (price != null) "🔒 $title" else title
        date_upload = dateFormat.tryParse(dateStr)
    }
}

private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("Asia/Tokyo")
}

@Serializable
class ViewerResponse(
    @ProtoNumber(3) val pageBlocks: List<PageBlock>,
)

@Serializable
class PageBlock(
    @ProtoNumber(3) val pages: List<MangaPage>,
)

@Serializable
class MangaPage(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(5) val key: String?,
    @ProtoNumber(6) val iv: String?,
)
