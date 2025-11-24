package eu.kanade.tachiyomi.extension.zh.komiic

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat

@Serializable
class ResponseDto(private val data: DataDto?, private val errors: List<ErrorDto>?) {
    fun getData() = data ?: throw Exception(errors!!.joinToString("\n") { it.message })
}

@Serializable
class ErrorDto(val message: String)

@Serializable
class DataDto(
    private val comics: List<MangaDto>?,
    val allCategory: List<ItemDto>?,
    private val searchComicsAndAuthors: DataDto?,
    val comicById: MangaDto?,
    val chaptersByComicId: List<ChapterDto>?,
    val reachedImageLimit: Boolean?,
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
) {
    val url get() = "/comic/$id"

    fun toSManga() = SManga.create().apply {
        url = this@MangaDto.url
        title = this@MangaDto.title
        thumbnail_url = this@MangaDto.imageUrl
        author = this@MangaDto.authors.joinToString { it.name }
        genre = this@MangaDto.categories.joinToString { it.name }
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
    fun toSChapter(mangaUrl: String, dateFormat: SimpleDateFormat) = SChapter.create().apply {
        val (suffix, typeName) = when (val type = this@ChapterDto.type) {
            "chapter" -> Pair("話", "連載")
            "book" -> Pair("卷", "單行本")
            else -> throw Exception("未知章節類型：$type")
        }
        url = "$mangaUrl/chapter/${this@ChapterDto.id}"
        name = "${this@ChapterDto.serial}$suffix（${this@ChapterDto.size}P）"
        scanlator = typeName
        date_upload = dateFormat.parse(this@ChapterDto.dateCreated)!!.time
        chapter_number = this@ChapterDto.serial.toFloatOrNull() ?: -1f
    }
}

@Serializable
class PageDto(
    val kid: String,
)
