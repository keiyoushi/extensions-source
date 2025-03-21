package eu.kanade.tachiyomi.extension.pt.sussyscan

import eu.kanade.tachiyomi.extension.pt.sussyscan.SussyToons.Companion.CDN_URL
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import org.jsoup.Jsoup

@Serializable
data class PageableDto<T>(
    @SerialName("pagina")
    val currentPage: Int = 0,
    @SerialName("totalPaginas")
    val lastPage: Int = 0,
    @JsonNames("resultado")
    private val resultados: T,
) {
    val results: T get() = resultados

    fun hasNextPage() = currentPage < lastPage

    fun toSMangaList() = (results as List<MangaDto>)
        .filterNot { it.slug.isNullOrBlank() }.map { it.toSManga() }
}

@Serializable
class WrapperDto(
    @SerialName("dataTop")
    val popular: PageableDto<List<MangaDto>>?,
    @JsonNames("atualizacoesInicial")
    private val dataLatest: PageableDto<List<MangaDto>>,
) {
    val latest: PageableDto<List<MangaDto>> get() = dataLatest
}

@Serializable
class MangaDto(
    @SerialName("obr_id")
    val id: Int,
    @SerialName("obr_descricao")
    val description: String?,
    @SerialName("obr_imagem")
    val thumbnail: String?,
    @SerialName("obr_nome")
    val name: String,
    @SerialName("obr_slug")
    val slug: String?,
    @SerialName("status")
    val status: MangaStatus,
    @SerialName("scan_id")
    val scanId: Int,
) {

    fun toSManga(): SManga {
        val sManga = SManga.create().apply {
            title = name
            thumbnail_url = thumbnail?.let {
                when {
                    it.startsWith("http") -> thumbnail
                    else -> "$CDN_URL/scans/$scanId/obras/${this@MangaDto.id}/$thumbnail"
                }
            }
            initialized = true
            url = "/obra/${this@MangaDto.id}/${this@MangaDto.slug}"
        }

        description?.let { Jsoup.parseBodyFragment(it).let { sManga.description = it.text() } }
        sManga.status = status.toStatus()

        return sManga
    }

    @Serializable
    class MangaStatus(
        @SerialName("stt_nome")
        val value: String?,
    ) {
        fun toStatus(): Int {
            return when (value?.lowercase()) {
                "em andamento" -> SManga.ONGOING
                "completo" -> SManga.COMPLETED
                "hiato" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }
}

@Serializable
class ChapterDto(
    @SerialName("cap_id")
    val id: Int,
    @SerialName("cap_nome")
    val name: String,
    @SerialName("cap_numero")
    val chapterNumber: Float?,
    @SerialName("cap_lancado_em")
    val updateAt: String,
)

@Serializable
class WrapperChapterDto(
    @SerialName("capitulos")
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterPageDto(
    @SerialName("cap_paginas")
    val pages: List<PageDto>,
    @SerialName("obra")
    val manga: MangaReferenceDto,
    @SerialName("cap_numero")
    val chapterNumber: Int,
) {
    @Serializable
    class MangaReferenceDto(
        @SerialName("obr_id")
        val id: Int,
        @SerialName("scan_id")
        val scanId: Int,
    )
}

@Serializable
class PageDto(
    val src: String,
    @SerialName("numero")
    val number: Int? = null,
) {
    fun isWordPressContent(): Boolean = number == null
}
