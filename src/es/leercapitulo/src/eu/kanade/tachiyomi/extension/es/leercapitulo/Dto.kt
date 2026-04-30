package eu.kanade.tachiyomi.extension.es.leercapitulo

import kotlinx.serialization.Serializable

@Serializable
class Dto(
    val label: String,
    val link: String,
    val thumbnail: String,
)
