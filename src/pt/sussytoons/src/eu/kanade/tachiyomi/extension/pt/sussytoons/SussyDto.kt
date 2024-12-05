package eu.kanade.tachiyomi.extension.pt.sussytoons

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WrapperDto<T>(
    @SerialName("pagina")
    val currentPage: Int = 0,
    @SerialName("totalPaginas")
    val lastPage: Int = 0,
    @SerialName("resultados")
    val results: T,
) {
    fun hasNextPage() = currentPage < lastPage
}

@Serializable
data class MangaDto(
    @SerialName("obr_id")
    val id: Int,
    @SerialName("obr_descricao")
    val description: String,
    @SerialName("obr_imagem")
    val thumbnail: String,
    @SerialName("obr_nome")
    val name: String,
    @SerialName("obr_slug")
    val slug: String,
    @SerialName("obr_status")
    val status: String,
    @SerialName("scan_id")
    val scanId: Int,
)

@Serializable
data class ChapterDto(
    @SerialName("cap_id")
    val id: Int,
    @SerialName("cap_nome")
    val name: String,
    @SerialName("cap_numero")
    val chapterNumber: Float,
    @SerialName("cap_criado_em")
    val updateAt: String,
)

@Serializable
data class WrapperPageDto(
    @SerialName("resultado")
    val result: ChapterPageDto,
)

@Serializable
data class ChapterPageDto(
    @SerialName("cap_numero")
    val chapterNumber: Int,
    @SerialName("cap_paginas")
    val pages: List<PageDto>,
    @SerialName("obra")
    val series: SeriesFragmentDto,

) {
    @Serializable
    data class SeriesFragmentDto(
        @SerialName("obr_id")
        val id: Int,
        @SerialName("scan_id")
        val scanId: Int,
    )
}

@Serializable
data class PageDto(
    val src: String,
    val mime: String? = null,
)
