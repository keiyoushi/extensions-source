package eu.kanade.tachiyomi.extension.pt.sussyscan

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class WrapperDto<T>(
    @SerialName("pagina")
    val currentPage: Int = 0,
    @SerialName("totalPaginas")
    val lastPage: Int = 0,
    @JsonNames("resultado")
    private val resultados: T,
) {
    val results: T get() = resultados

    fun hasNextPage() = currentPage < lastPage
}

@Serializable
class MangaDto(
    @SerialName("obr_id")
    val id: Int,
    @SerialName("obr_descricao")
    val description: String,
    @SerialName("obr_imagem")
    val thumbnail: String,
    @SerialName("obr_nome")
    val name: String,
    @SerialName("obr_slug")
    val slug: String?,
    @SerialName("status")
    val status: MangaStatus,
) {
    @Serializable
    class MangaStatus(
        @SerialName("stt_nome")
        val value: String,
    ) {
        fun toStatus(): Int {
            return when (value.lowercase()) {
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
)

@Serializable
class PageDto(
    val src: String,
)
