package eu.kanade.tachiyomi.extension.all.unionmangas

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class NextData<T>(val props: Props<T>, val query: QueryDto) {
    val data get() = props.pageProps
}

@Serializable
data class Props<T>(val pageProps: T)

@Serializable
data class PopularMangaProps(@SerialName("data_popular") val mangas: List<PopularMangaDto>)

@Serializable
data class LatestUpdateProps(@SerialName("data_lastuppdate") val latestUpdateDto: MangaListDto)

@Serializable
data class MangaDetailsProps(@SerialName("dataManga") val mangaDetailsDto: MangaDetailsDto)

@Serializable
data class ChaptersProps(@SerialName("data") val pageListData: String)

@Serializable
abstract class Pageable {
    abstract val currentPage: String
    abstract val totalPage: Int
    fun hasNextPage() =
        try { (currentPage!!.toInt() + 1) < totalPage } catch (_: Exception) { false }
}

@Serializable
class ChapterPageDto(
    val totalRecode: Int = 0,
    override val currentPage: String,
    override val totalPage: Int,
    @SerialName("data") val chapters: List<ChapterDto> = emptyList(),
) : Pageable() {
    fun toSChapter(langOption: LanguageOption): List<SChapter> =
        chapters.map { chapter ->
            SChapter.create().apply {
                name = chapter.name
                date_upload = chapter.date.toDate()
                url = "/${langOption.infix}${chapter.toChapterUrl(langOption.chpPrefix)}"
            }
        }

    private fun String.toDate(): Long =
        try { UnionMangas.dateFormat.parse(trim())!!.time } catch (_: Exception) { 0L }

    private fun ChapterDto.toChapterUrl(prefix: String) = "/${this.slugManga}/$prefix-${this.id}"
}

@Serializable
data class ChapterDto(
    val date: String,
    val slug: String,
    @SerialName("idDoc") val slugManga: String,
    @SerialName("idDetail") val id: String,
    @SerialName("nameChapter") val name: String,
)

@Serializable
data class QueryDto(
    val type: String,
)

@Serializable
class MangaListDto(
    override val currentPage: String,
    override val totalPage: Int,
    @SerialName("data") val mangas: List<MangaDto>,
) : Pageable() {
    fun toSManga(siteLang: String) = mangas.map { dto ->
        SManga.create().apply {
            title = dto.title
            thumbnail_url = dto.thumbnailUrl
            status = dto.status
            url = mangaUrlParse(dto.slug, siteLang)
            genre = dto.genres
        }
    }
}

@Serializable
data class PopularMangaDto(
    @SerialName("document") val details: MangaDto,
)

@Serializable
data class MangaDto(
    @SerialName("name") val title: String,
    @SerialName("image") private val _thumbnailUrl: String,
    @SerialName("idDoc") val slug: String,
    @SerialName("genres") private val _genres: String,
    @SerialName("status") val _status: String,
) {
    val thumbnailUrl get() = "${UnionMangas.apiUrl}$_thumbnailUrl"
    val genres get() = _genres.split(",").joinToString { it.trim() }
    val status get() = toSMangaStatus(_status)
}

@Serializable
data class MangaDetailsDto(
    @SerialName("name") val title: String,
    @SerialName("image") private val _thumbnailUrl: String,
    @SerialName("idDoc") val slug: String,
    @SerialName("lsgenres") private val _genres: List<Prop>,
    @SerialName("lsstatus") private val _status: List<Prop>,
) {

    val thumbnailUrl get() = "${UnionMangas.apiUrl}$_thumbnailUrl"
    val genres get() = _genres.joinToString { it.name }
    val status get() = toSMangaStatus(_status.first().name)

    @Serializable
    data class Prop(
        val name: String,
    )
}

@Serializable
data class ChaptersDto(
    @SerialName("dataManga") val data: PageDto,
    private var delimiter: String = "",
) {
    val images get() = data.getImages(delimiter)
}

@Serializable
data class PageDto(
    @SerialName("source") private val imgData: String,
) {
    fun getImages(delimiter: String): List<String> = imgData.split(delimiter)
}

private fun mangaUrlParse(slug: String, pathSegment: String) = "/$pathSegment/$slug"

private fun toSMangaStatus(status: String) =
    when (status.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
