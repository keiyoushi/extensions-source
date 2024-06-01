package eu.kanade.tachiyomi.extension.all.unionmangas

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class NextData<T>(val props: Props<T>, val query: QueryDto) {
    val data get() = props.pageProps
}

@Serializable
class Props<T>(val pageProps: T)

@Serializable
class PopularMangaDto(
    @SerialName("data") val mangas: List<MangaDto>,
    override var currentPage: Int,
    override var totalPage: Int,
) : Pageable()

@Serializable
class LatestUpdateProps(@SerialName("data_lastuppdate") val latestUpdateDto: MangaListDto)

@Serializable
class MangaDetailsProps(
    @SerialName("infoDoc") val mangaDetailsDto: MangaDto,
)

@Serializable
class ChaptersProps(@SerialName("data") val pageListData: String)

@Serializable
open class Pageable1<T>(
    var currentPage: Int,
    var totalPage: Int,
    val data: T,
) {
    fun hasNextPage() =
        try { (currentPage + 1) <= totalPage } catch (_: Exception) { false }
}

@Serializable
abstract class Pageable {
    abstract var currentPage: Int
    abstract var totalPage: Int

    fun hasNextPage() =
        try { (currentPage + 1) < totalPage } catch (_: Exception) { false }
}

@Serializable
class ChapterDto(
    val date: String,
    @SerialName("idDoc") val slugManga: String,
    @SerialName("idDetail") val id: String,
    @SerialName("nameChapter") val name: String,
) {
    fun toChapterUrl(lang: String) = "/$lang/${this.slugManga}/$id"
}

@Serializable
class QueryDto(
    val type: String,
)

@Serializable
class MangaListDto(
    override var currentPage: Int,
    override var totalPage: Int,
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
class MangaDto(
    @SerialName("name") val title: String,
    @SerialName("image") private val _thumbnailUrl: String,
    @SerialName("idDoc") val slug: String,
    @SerialName("genresName") val genres: String,
    @SerialName("status") val _status: String,
) {
    val thumbnailUrl get() = "${UnionMangas.apiUrl}$_thumbnailUrl"
    val status get() = toSMangaStatus(_status)
}

@Serializable
class MangaDetailsDto(
    @SerialName("name") val title: String,
    @SerialName("image") private val _thumbnailUrl: String,
    @SerialName("idDoc") val slug: String,
    @SerialName("lsgenres") private val _genres: List<Prop>,
    @SerialName("lsstatus") private val _status: List<Prop>,
) {

    val thumbnailUrl get() = "${UnionMangas.apiUrl}$_thumbnailUrl"
    val genres get() = _genres.joinToString { it.name }
    val status get() = toSMangaStatus(_status.firstOrNull()?.name ?: "")

    @Serializable
    class Prop(
        val name: String,
    )
}

@Serializable
class ChaptersDto(
    @SerialName("dataManga") val data: PageDto,
    private var delimiter: String = "",
) {
    val images get() = data.getImages(delimiter)
}

@Serializable
class PageDto(
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
