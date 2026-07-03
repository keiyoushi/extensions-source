package eu.kanade.tachiyomi.extension.zh.komiic

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class DataDto(
    private val comics: List<MangaDto>?,
    val allCategory: List<ItemDto>?,
    private val searchComicsAndAuthors: DataDto?,
    val comicById: MangaDto?,
    val chaptersByComicId: List<ChapterDto>?,
    val imagesByChapterId: List<PageDto>?,
) {
    fun getListing(): List<MangaDto> = comics ?: searchComicsAndAuthors!!.comics!!
}

@Serializable
class JwtPayload(val exp: Long)

@Serializable
class ItemDto(val id: String, val name: String)

@Serializable
class MangaDto(
    private val id: String,
    private val title: String,
    private val description: String,
    private val status: String,
    private val imageUrl: String,
    private val authors: List<ItemDto>,
    private val categories: List<ItemDto>,
    private val warnings: List<String>,
) {
    val url get() = "/comic/$id"

    fun toSManga() = SManga.create().apply {
        url = this@MangaDto.url
        title = this@MangaDto.title
        thumbnail_url = this@MangaDto.imageUrl
        author = this@MangaDto.authors.joinToString("，") { it.name }
        genre = this@MangaDto.categories.map(ItemDto::name).plus(warnings).joinToString()
        description = this@MangaDto.description
        status = when (this@MangaDto.status) {
            "ONGOING" -> SManga.ONGOING
            "END" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        initialized = this@MangaDto.description.isNotEmpty()
    }
}

@Serializable
class ChapterDto(
    private val id: String,
    val serial: String,
    val type: String,
    private val size: Int,
    private val dateCreated: String,
) {
    fun toSChapter(mangaUrl: String) = SChapter.create().apply {
        url = "$mangaUrl/chapter/${this@ChapterDto.id}"
        name = when (this@ChapterDto.type) {
            "chapter" -> "第 ${this@ChapterDto.serial} 話"
            "book" -> "第 ${this@ChapterDto.serial} 卷"
            else -> this@ChapterDto.serial
        }
        scanlator = "${this@ChapterDto.size}P"
        date_upload = Instant.parse(this@ChapterDto.dateCreated).toEpochMilliseconds()
        chapter_number = this@ChapterDto.serial.toFloatOrNull() ?: -1f
    }
}

@Serializable
class PageDto(
    val kid: String,
)
