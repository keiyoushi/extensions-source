package eu.kanade.tachiyomi.extension.pt.randomscan.dto

import kotlinx.serialization.Serializable

@Serializable
data class Genero(
    val id: Int,
    val name: String,
)

@Serializable
data class Capitulo(
    val num: Double,
    val data: String,
    val views: Int,
    val slug: String,
    val id: Int,
)

@Serializable
data class Manga(
    val id: Int,
    val capa: String,
    val titulo: String,
    val views: Int,
    val alternativo: String?,
    val autor: String?,
    val artista: String?,
    val estudio: String?,
    val status: String,
    val sinopse: String,
    val ano: Int?,
    val rank: Int?,
    val dia: String?,
    val tipo: String,
    val generos: List<Genero>,
    val caps: List<Capitulo>,
)

@Serializable
data class Obra(
    val id: Int,
    val titulo: String,
)

@Serializable
data class CapituloPagina(
    val id: Int,
    val titulo: String,
    val next_cap: String?,
    val before_cap: String?,
    val obra: Obra,
    val files: Int,
)

@Serializable
data class MainPageManga(
    val id: Int,
    val title: String,
    val capa: String,
    val slug: String,
)

@Serializable
data class MainPage(
    val lancamentos: List<MainPageManga>,
    val top_10: List<MainPageManga>,
)

@Serializable
data class SearchResponseManga(
    val id: Int,
    val titulo: String,
    val capa: String,
    val slug: String,
)

@Serializable
data class SearchResponse(
    val obras: List<SearchResponseManga>,
)
