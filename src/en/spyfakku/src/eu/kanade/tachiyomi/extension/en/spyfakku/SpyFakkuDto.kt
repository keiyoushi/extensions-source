package eu.kanade.tachiyomi.extension.en.spyfakku

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Hentai(
    val id: Int,
    val slug: String,
    val title: String,
    val createdAt: Long,
    val pages: Int,
    val artists: List<Name>?,
    val circle: List<Name>?,
    val magazines: List<Name>?,
    val parodies: List<Name>?,
    val tags: List<Name>?,
)

@Serializable
class Name(
    @SerialName("name") val value: String,
)
