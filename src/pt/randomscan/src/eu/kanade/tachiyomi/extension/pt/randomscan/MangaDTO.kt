package eu.kanade.tachiyomi.extension.pt.randomscan

import kotlinx.serialization.Serializable

@Serializable
data class GeneroDTO(
    val name: String,
)

@Serializable
data class CapituloDTO(
    val num: Double,
    val data: String,
    val slug: String,
)

@Serializable
data class MangaDTO(
    val capa: String,
    val titulo: String,
    val autor: String?,
    val artista: String?,
    val status: String,
    val sinopse: String,
    val tipo: String,
    val generos: List<GeneroDTO>,
    val caps: List<CapituloDTO>,
)

@Serializable
data class ObraDTO(
    val id: Int,
)

@Serializable
data class CapituloPaginaDTO(
    val id: Int,
    val obra: ObraDTO,
    val files: Int,
)

@Serializable
data class MainPageMangaDTO(
    val title: String,
    val capa: String,
    val slug: String,
)

@Serializable
data class MainPageDTO(
    val lancamentos: List<MainPageMangaDTO>,
    val top_10: List<MainPageMangaDTO>,
)

@Serializable
data class SearchResponseMangaDTO(
    val titulo: String,
    val capa: String,
    val slug: String,
)

@Serializable
data class SearchResponseDTO(
    val obras: List<SearchResponseMangaDTO>,
)
