package eu.kanade.tachiyomi.multisrc.greenshit

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

// ============================= API Response Wrapper =========================

@Serializable
class ResultDto<T>(
    @SerialName("pagina")
    val currentPage: Int = 0,
    @SerialName("totalPaginas")
    val lastPage: Int = 0,
    @SerialName("obras")
    @JsonNames("resultado", "resultados")
    private val obras: T,
) {
    val results: T get() = obras

    fun hasNextPage() = currentPage < lastPage

    companion object {
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }
}

// ============================= Extension Functions ==========================

fun ResultDto<List<MangaDto>>.toSMangaList(): List<SManga> {
    return results.map { it.toSManga() }
}

// ============================= Manga Models =================================

@Serializable
class MangaDto(
    @SerialName("obr_id")
    val id: Int,
    @SerialName("obr_nome")
    val name: String,
    @SerialName("obr_slug")
    var slug: String? = null,
    @SerialName("obr_imagem")
    val thumbnail: String? = null,
    @SerialName("obr_descricao")
    val description: String? = null,
    @SerialName("scan_id")
    val scanId: Int,
    @SerialName("status")
    val status: MangaStatus? = null,
    @SerialName("tags")
    val genres: List<Genre> = emptyList(),
    @SerialName("capitulos")
    val chapters: List<ChapterDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        url = "/obra/${slug ?: id}"
        thumbnail_url = buildThumbnailUrl()
        genre = genres.joinToString()
        initialized = true

        this.description = this@MangaDto.description?.takeIf { it.isNotBlank() }?.let {
            Jsoup.parseBodyFragment(it).text()
        }

        this@MangaDto.status?.let { this.status = it.toStatus() }
    }

    private fun buildThumbnailUrl(): String? {
        return thumbnail?.let {
            when {
                it.startsWith("http") -> it
                it.contains("/") -> "https://api2.sussytoons.wtf/cdn/$it" else -> "https://api2.sussytoons.wtf/cdn/scans/$scanId/obras/$id/$it?width=300"
            }
        }
    }

    fun toSChapterList(): List<SChapter> {
        return chapters?.map { it.toSChapter() }
            ?.sortedByDescending { it.chapter_number }
            ?: emptyList()
    }

    @Serializable
    class Genre(@SerialName("tag_nome") val value: String) {
        override fun toString() = value
    }

    @Serializable
    class MangaStatus(@SerialName("stt_nome") val value: String?) {
        fun toStatus(): Int = when (value?.lowercase()) {
            "em andamento" -> SManga.ONGOING
            "completo" -> SManga.COMPLETED
            "hiato" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

// ============================= Chapter Models ===============================

@Serializable
class ChapterDto(
    @SerialName("cap_id")
    val id: Int,
    @SerialName("cap_nome")
    val name: String,
    @SerialName("cap_numero")
    val numero: Float? = null,
    @JsonNames("cap_lancado_em", "cap_liberar_em", "cap_criado_em")
    val releaseDate: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        this.name = this@ChapterDto.name
        url = "/capitulo/$id"

        numero?.let { chapter_number = it }

        date_upload = releaseDate?.let { ResultDto.DATE_FORMAT.tryParse(it) } ?: 0L
    }
}

// ============================= Filters DTO ================================

@Serializable
class FiltersDto(
    @SerialName("generos") val genres: List<Genre> = emptyList(),
    @SerialName("formatos") val formats: List<Format> = emptyList(),
    @SerialName("status") val statuses: List<Status> = emptyList(),
    @SerialName("parceiras") val partners: List<Partner> = emptyList(),
    @SerialName("tags") val tags: List<Tag> = emptyList(),
)

@Serializable
class Genre(
    @SerialName("gen_id") val id: Int,
    @SerialName("gen_nome") val name: String,
)

@Serializable
class Format(
    @SerialName("formt_id") val id: Int,
    @SerialName("formt_nome") val name: String,
)

@Serializable
class Status(
    @SerialName("stt_id") val id: Int,
    @SerialName("stt_nome") val name: String,
)

@Serializable
class Partner(
    @SerialName("parceira_id") val id: Int,
    @SerialName("parceira_nome") val name: String,
)

@Serializable
class Tag(
    @SerialName("tag_id") val id: Int,
    @SerialName("tag_nome") val name: String,
)

@Serializable
class ChapterPageDto(
    @SerialName("paginas") val pages: List<PageDto>,
) {
    fun toPageList(): List<Page> = pages.map { it.toPage() }
}

@Serializable
class PageDto(
    @SerialName("pagina_url") val url: String,
) {
    fun toPage() = Page(0, url, null)
}
