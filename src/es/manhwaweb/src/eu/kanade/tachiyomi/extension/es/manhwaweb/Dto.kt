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
        url = slug.removePrefix("/").replace("manga/", "manhwa/")
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
    @SerialName("id_rel") val slug: String,
    @SerialName("name_manhwa") private val name: String,
    @SerialName("img") private val thumbnail: String,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        thumbnail_url = thumbnail
        url = "manhwa/$slug"
    }
}

@Serializable
class PayloadSearchDto(
    val data: List<SearchComicDto>,
    @SerialName("next") val hasNextPage: Boolean,
)

@Serializable
class SearchComicDto(
    @SerialName("real_id") val slug: String,
    @SerialName("the_real_name") private val name: String,
    @SerialName("_imagen") private val thumbnail: String,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        thumbnail_url = thumbnail
        url = "manhwa/$slug"
    }
}

@Serializable
class ComicDetailsDto(
    @SerialName("_id") val id: String,
    @SerialName("real_id") val slug: String,
    @SerialName("name_esp") private val title: String,
    @SerialName("_sinopsis") private val description: String? = null,
    @SerialName("_status") private val status: String,
    @SerialName("_name") private val alternativeName: String? = null,
    @SerialName("_imagen") private val thumbnail: String,
    @SerialName("_categoris") private val genres: List<Map<Int, String>>,
    @SerialName("_extras") private val extras: ComicDetailsExtrasDto,
    val chapters: List<ChapterDto>,
) {
    fun toSManga() = SManga.create().apply {
        url = "manhwa/$slug"
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
        initialized = true
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
class ChapterDto(
    @SerialName("chapter") val number: Float,
    @SerialName("link_raw") val rawUrl: String? = null,
    private val link: String? = null,
    private val create: Long?,
    private val versions: List<ChapterVersionDto>? = null,
) {
    val espUrl get() = link ?: versions?.firstOrNull()?.link
    val createdAt get() = create ?: versions?.firstOrNull()?.create
}

@Serializable
class ChapterVersionDto(
    val link: String? = null,
    val create: Long? = null,
)

@Serializable
class PayloadPageDto(
    @SerialName("chapter") val data: PageDto,
)

@Serializable
class PageDto(
    @SerialName("img") val images: List<String>,
)
