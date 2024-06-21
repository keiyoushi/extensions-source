package eu.kanade.tachiyomi.extension.es.manhwaweb

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PayloadPopularDto(
    @SerialName("top") val data: PopularDto,
)

@Serializable
class PopularDto(
    @SerialName("manhwas_esp") val weekly: List<PopularComicDto>,
    @SerialName("manhwas_raw") val total: List<PopularComicDto>,
)

@Serializable
class PopularComicDto(
    @SerialName("link") val slug: String,
    @SerialName("numero") val views: Int,
    private val name: String,
    @SerialName("imagen") private val thumbnail: String,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        thumbnail_url = thumbnail
        url = slug.removePrefix("/")
    }
}

@Serializable
class PayloadLatestDto(
    @SerialName("manhwas") val data: LatestDto,
)

@Serializable
class LatestDto(
    @SerialName("manhwas_esp") val esp: List<LatestComicDto>,
    @SerialName("manhwas_raw") val raw18: List<LatestComicDto>,
    @SerialName("_manhwas") val esp18: List<LatestComicDto>,
)

@Serializable
class LatestComicDto(
    @SerialName("create") val latestChapterDate: Long,
    @SerialName("id_manhwa") val slug: String,
    @SerialName("_plataforma") val platform: String,
    @SerialName("name_manhwa") private val name: String,
    @SerialName("img") private val thumbnail: String,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        thumbnail_url = thumbnail
        val type = if (platform == "toptoon" || platform == "lezhin") "manhwa" else "manga"
        url = "$type/$slug"
    }
}

@Serializable
class PayloadSearchDto(
    val data: List<SearchComicDto>,
    @SerialName("next") val hasNextPage: Boolean,
)

@Serializable
class SearchComicDto(
    @SerialName("_id") val slug: String,
    @SerialName("_plataforma") val platform: String,
    @SerialName("the_real_name") private val name: String,
    @SerialName("_imagen") private val thumbnail: String,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        thumbnail_url = thumbnail
        val type = if (platform == "toptoon" || platform == "lezhin") "manhwa" else "manga"
        url = "$type/$slug"
    }
}

@Serializable
class ComicDetailsDto(
    @SerialName("name_esp") private val title: String,
    @SerialName("_sinopsis") private val description: String? = null,
    @SerialName("_status") private val status: String,
    @SerialName("_name") private val alternativeName: String? = null,
    @SerialName("_imagen") private val thumbnail: String,
    @SerialName("_categoris") private val genres: List<Map<Int, String>>,
    @SerialName("_extras") private val extras: ComicDetailsExtrasDto,
) {
    fun toSManga() = SManga.create().apply {
        title = this@ComicDetailsDto.title
        thumbnail_url = thumbnail
        description = this@ComicDetailsDto.description
        if (!alternativeName.isNullOrBlank()) {
            if (!description.isNullOrBlank()) description += "\n\n"
            description += "Nombres alternativos: $alternativeName"
        }
        status = parseStatus(this@ComicDetailsDto.status)
        genre = genres.joinToString { it.values.first() }
        author = extras.authors.joinToString()
    }

    private fun parseStatus(status: String) = when (status) {
        "publicandose" -> SManga.ONGOING
        "finalizado" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class ComicDetailsExtrasDto(
    @SerialName("autores") val authors: List<String>,
)

@Serializable
class PayloadChapterDto(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    @SerialName("chapter") val number: Float,
    @SerialName("link") val espUrl: String? = null,
    @SerialName("link_raw") val rawUrl: String? = null,
    @SerialName("create") val createdAt: Long?,
)

@Serializable
class PayloadPageDto(
    @SerialName("chapter") val data: PageDto,
)

@Serializable
class PageDto(
    @SerialName("img") val images: List<String>,
)
