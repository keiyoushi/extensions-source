package eu.kanade.tachiyomi.extension.all.unionmangas

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

    fun hasNextPageToLatestUpdates() = latestUpdateRawContent?.currentPage?.toInt()!! < latestUpdateRawContent?.totalPage!!

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

    private fun mangaUrlParse(slug: String, pathSegment: String) = "/$pathSegment/$slug"
}

@Serializable
data class QueryDto(
    val type: String,
)

@Serializable
data class Props(val pageProps: PageProps)

@Serializable
data class PageProps(
    @SerialName("data_lastuppdate") val latestUpdateDto: LatestUpdate?,
    @SerialName("data_popular") val popularMangaDto: List<PopularMangaDto>?,
    @SerialName("dataManga") val mangaDetailsDto: MangaDetailsDto?,
)

@Serializable
data class LatestUpdate(
    val currentPage: String,
    val totalPage: Int,
    @SerialName("data") val mangas: List<MangaDto>,
)

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

private fun statusToModel(status: String) =
    when (status.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
