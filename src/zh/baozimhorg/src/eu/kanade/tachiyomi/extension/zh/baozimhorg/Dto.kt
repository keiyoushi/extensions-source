package eu.kanade.tachiyomi.extension.zh.baozimhorg

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class ResponseDto<T>(val data: T)

@Serializable
class ChapterListDto(
    private val id: Int,
    private val slug: String,
    private val chapters: List<ChapterDto>,
) {
    fun toChapterList(): List<SChapter> {
        val mangaId = id.toString()
        val mangaSlug = slug
        return chapters.asReversed().map { it.toSChapter(mangaSlug, mangaId) }
    }
}

@Serializable
class ChapterDto(
    private val id: Int,
    private val attributes: AttributesDto,
) {
    fun toSChapter(mangaSlug: String, mangaId: String) = attributes.toSChapter(mangaSlug, mangaId, id.toString())
}

@Serializable
class AttributesDto(
    private val title: String,
    private val slug: String,
    private val updatedAt: String,
) {
    fun toSChapter(mangaSlug: String, mangaId: String, chapterId: String) = SChapter.create().apply {
        url = "$mangaSlug/$slug#$mangaId/$chapterId"
        name = title
        date_upload = dateFormat.parse(updatedAt)!!.time
    }
}

// Static field, no need for lazy
private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class PageListDto(val info: PageListInfoDto)

@Serializable
class PageListInfoDto(val images: PageListInfoImagesDto)

@Serializable
class PageListInfoImagesDto(val images: List<ImageDto>)

@Serializable
class ImageDto(private val url: String, private val order: Int) {
    fun toPage() = Page(order, imageUrl = "https://f40-1-4.g-mh.online$url")
}
