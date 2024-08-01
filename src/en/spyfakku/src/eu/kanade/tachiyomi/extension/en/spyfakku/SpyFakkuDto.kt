package eu.kanade.tachiyomi.extension.en.spyfakku

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class HentaiLib(
    val data: List<JsonElement>,
)

@Serializable
class Indexes(
    val archives: Int,
    val page: Int,
    val limit: Int,
    val total: Int,
)

@Serializable
class ShortHentaiIndexes(
    val id: Int,
    val hash: Int,
    val title: Int,
)

@Serializable
class HentaiIndexes(
    val id: Int,
    val hash: Int,
    val title: Int,
    val description: Int,
    val released_at: Int,
    val created_at: Int,
    val pages: Int,
    val size: Int,
    val publishers: Int?,
    val artists: Int?,
    val circles: Int?,
    val magazines: Int?,
    val parodies: Int?,
    val events: Int?,
    val tags: Int?,
    val images: Int,
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
    val size: Long = 0L,
    val publishers: List<String>?,
    val artists: List<String>?,
    val circles: List<String>?,
    val magazines: List<String>?,
    val parodies: List<String>?,
    val events: List<String>?,
    val tags: List<String>?,
    val images: List<String>,
)

@Serializable
class ShortHentai(
    val id: String,
    val hash: String,
    val title: String,
)

@Serializable
class ImageIndex(
    val filename: Int,
)

@Serializable
class NameIndex(
    val name: Int,
)
