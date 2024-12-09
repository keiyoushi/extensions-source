package eu.kanade.tachiyomi.extension.pt.randomscan

import kotlinx.serialization.Serializable

@Serializable
class GeneroDTO(
    val name: String,
)

@Serializable
class CapituloDTO(
    val num: Double,
    val data: String,
    val slug: String,
)

@Serializable
class MangaDTO(
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
class ObraDTO(
    val id: Int,
)

@Serializable
class CapituloPaginaDTO(
    val id: Int,
    val obra: ObraDTO,
    val files: Int,
)

@Serializable
class MainPageMangaDTO(
    val title: String,
    val capa: String,
    val slug: String,
)

@Serializable
class MainPageDTO(
    val lancamentos: List<MainPageMangaDTO>,
    val top_10: List<MainPageMangaDTO>,
)

@Serializable
class SearchResponseMangaDTO(
    val titulo: String,
    val capa: String,
    val slug: String,
)

@Serializable
class SearchResponseDTO(
    val obras: List<SearchResponseMangaDTO>,
)
