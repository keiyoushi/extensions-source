package eu.kanade.tachiyomi.extension.es.leercapitulo.dto

import kotlinx.serialization.Serializable

@Serializable
data class MangaDto(
    val label: String,
    val link: String,
    val thumbnail: String,
    val value: String,
)
