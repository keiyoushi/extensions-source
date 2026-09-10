package eu.kanade.tachiyomi.extension.es.shadowmanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class SeriesWrapper(
    val series: List<Series>,
)

@Serializable
class RecentChaptersResponse(
    val items: List<RecentChapterItem> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 24,
    val totalPages: Int = 1,
)

@Serializable
class RecentChapterItem(
    val serie: Series,
)

@Serializable
class Series(
    val id: Int,
    @SerialName("titulo") val title: String,
    @SerialName("portadaUrl") private val thumbnailUrl: String? = null,
    @SerialName("descripcion") private val description: String? = null,
    @SerialName("autor") private val author: String? = null,
    @SerialName("generos") private val genres: String? = null,
    @SerialName("estado") private val status: String? = null,
    @SerialName("capitulos") val chapters: List<Chapter> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = this@Series.title
        url = id.toString()
        thumbnail_url = this@Series.thumbnailUrl
    }

    fun toSMangaDetails() = SManga.create().apply {
        title = this@Series.title
        thumbnail_url = this@Series.thumbnailUrl
        description = this@Series.description
        author = this@Series.author
        genre = genres?.split(",")?.joinToString { it.trim() }
        status = this@Series.status.parseStatus()
    }

    fun getGenreList(): List<String> = genres.orEmpty().split(",").map { it.trim() }

    private fun String?.parseStatus(): Int = when (this?.lowercase()) {
        "en curso" -> SManga.ONGOING
        "completado" -> SManga.COMPLETED
        "pausada" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.ROOT)

@Serializable
class Chapter(
    private val id: Int,
    @SerialName("numeroCapitulo") val chapterNumber: Float,
    @SerialName("titulo") private val title: String?,
    @SerialName("fechaSubida") private val uploadDate: String?,
) {
    fun toSChapter(mangaId: Int) = SChapter.create().apply {
        name = buildString {
            append("Cap. ")
            append(chapterNumber.toString().removeSuffix(".0"))
            title?.let {
                append(" - $it")
            }
        }
        url = "$mangaId/$id"
        date_upload = dateFormat.tryParse(uploadDate)
    }
}

@Serializable
class PagesWrapper(
    @SerialName("paginas") val pages: List<String>,
)

@Serializable
class AdultCatalogResponse(
    val total: Int = 0,
    val items: List<AdultSeriesItem> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 48,
    val totalPages: Int = 1,
    val pageTokens: PageTokens? = null,
)

@Serializable
class PageTokens(
    val next: String? = null,
)

@Serializable
class AdultSeriesItem(
    val id: Int = 0,
    @SerialName("titulo") val title: String,
    @SerialName("portadaUrl") private val thumbnailUrl: String? = null,
    @SerialName("autor") private val author: String? = null,
    @SerialName("generos") private val genres: String? = null,
    val externo: Boolean = false,
    val smId: Long? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = this@AdultSeriesItem.title
        url = if ((externo || id == 0) && smId != null) "ext/$smId" else id.toString()
        thumbnail_url = this@AdultSeriesItem.thumbnailUrl
    }

    fun getGenreList(): List<String> = genres.orEmpty().split(",").map { it.trim() }
}

@Serializable
class OneshotDetails(
    val smId: Long,
    @SerialName("titulo") val title: String,
    @SerialName("autor") private val author: String? = null,
    @SerialName("descripcion") private val description: String? = null,
    @SerialName("generos") private val genres: String? = null,
    @SerialName("portadaUrl") private val thumbnailUrl: String? = null,
    @SerialName("capituloId") val chapterId: Int = 1,
    @SerialName("totalPaginas") val totalPages: Int = 0,
) {
    fun toSMangaDetails() = SManga.create().apply {
        title = this@OneshotDetails.title
        thumbnail_url = this@OneshotDetails.thumbnailUrl
        description = this@OneshotDetails.description
        author = this@OneshotDetails.author
        genre = genres?.split(",")?.joinToString { it.trim() }
        status = SManga.COMPLETED
    }
}
