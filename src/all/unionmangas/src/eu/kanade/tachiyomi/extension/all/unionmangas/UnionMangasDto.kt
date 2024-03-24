package eu.kanade.tachiyomi.extension.all.unionmangas

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UnionMangasDto(
    val props: Props,
    val query: QueryDto,
) {
    private val latestUpdateRawContent get() = props.pageProps.latestUpdateDto
    private val popularMangaRawContent get() = props.pageProps.popularMangaDto
    private val latestUpdateDto get() = latestUpdateRawContent?.mangas
    private val popularMangaDto get() = popularMangaRawContent?.map { it.details }
    private val mangaDetailsDto get() = props.pageProps.mangaDetailsDto

    fun hasNextPageToLatestUpdates() = latestUpdateRawContent?.hasNextPage() ?: false

    fun hasNextPageToPopularMangas() = false

    fun toPopularMangaModel(): List<SManga> = popularMangaDto?.map(::mangaModelParse) ?: emptyList()

    fun toLatestUpdatesModel(): List<SManga> = latestUpdateDto?.map(::mangaModelParse) ?: emptyList()

    fun toMangaDetailsModel() = SManga.create().apply {
        title = mangaDetailsDto!!.title
        genre = mangaDetailsDto!!.genres
        thumbnail_url = mangaDetailsDto!!.thumbnailUrl
        url = mangaUrlParse(mangaDetailsDto!!.slug, query.type)
        status = mangaDetailsDto!!.status
    }

    private fun mangaModelParse(dto: MangaDto): SManga = SManga.create().apply {
        title = dto.title
        thumbnail_url = dto.thumbnailUrl
        status = dto.status
        url = mangaUrlParse(dto.slug, query.type)
        genre = dto.genres
    }
}

@Serializable
data class ChapterPageDto(
    val totalRecode: Int = 0,
    val currentPage: String = "0",
    val totalPage: Int = 0,
    @SerialName("data") val chapters: List<ChapterDto> = emptyList(),
) {
    fun toModel(langOption: LanguageOption): List<SChapter> =
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
data class Props(val pageProps: PageProps)

@Serializable
data class PageProps(
    @SerialName("data_lastuppdate") val latestUpdateDto: PageableMangaListDto?,
    @SerialName("data_popular") val popularMangaDto: List<PopularMangaDto>?,
    @SerialName("dataManga") val mangaDetailsDto: MangaDetailsDto?,
    @SerialName("data") val pageListData: String?,
)

@Serializable
data class PageableMangaListDto(
    val currentPage: String?,
    val totalPage: Int,
    @SerialName("data") val mangas: List<MangaDto>,
) {
    fun toModels(siteLang: String) = mangas.map { dto ->
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
    val status get() = statusToModel(_status)
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
    val status get() = statusToModel(_status.first().name)

    @Serializable
    data class Prop(
        val name: String,
    )
}

@Serializable
data class PageDataDto(
    @SerialName("dataManga") val data: PageDto,
)

@Serializable
data class PageDto(
    @SerialName("source") private val imgData: String,
) {
    fun getImages(delimiter: String): List<String> = imgData.split(delimiter)
}

private fun mangaUrlParse(slug: String, pathSegment: String) = "/$pathSegment/$slug"

private fun statusToModel(status: String) =
    when (status.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

private fun hasNextPage(currentPage: String?, totalPage: Int) =
    try { (currentPage!!.toInt() + 1) < totalPage } catch (_: Exception) { false }

fun PageableMangaListDto.hasNextPage() = hasNextPage(currentPage, totalPage)
fun ChapterPageDto.hasNextPage() = hasNextPage(currentPage, totalPage)
