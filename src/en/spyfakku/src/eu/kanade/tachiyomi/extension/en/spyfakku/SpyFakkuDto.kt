package eu.kanade.tachiyomi.extension.en.spyfakku

import kotlinx.serialization.Serializable

@Serializable
class HentaiLib(
    val archives: List<ShortHentai>,
)

@Serializable
class Hentai(
    val id: Int,
    val hash: String,
    val title: String,
    val description: String?,
    val released_at: String,
    val created_at: String,
    val pages: Int,
    val size: Int = 0,
    val publishers: List<Name>?,
    val artists: List<Name>?,
    val circles: List<Name>?,
    val magazines: List<Name>?,
    val parodies: List<Name>?,
    val events: List<Name>?,
    val tags: List<Name>?,
    val images: List<Image>,
)

@Serializable
class ShortHentai(
    val id: Int,
    val hash: String,
    val title: String,
)

@Serializable
class Image(
    val filename: String,
)

@Serializable
class Name(
    val name: String,
)
