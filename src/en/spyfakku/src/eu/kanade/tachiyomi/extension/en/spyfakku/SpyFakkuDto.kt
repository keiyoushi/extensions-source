package eu.kanade.tachiyomi.extension.en.spyfakku

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class HentaiLib(
    val archives: List<Hentai>,
    val page: Int,
    val limit: Int,
    val total: Int,
)

@Serializable
class Hentai(
    val id: Int,
    val hash: String,
    val title: String,
    val thumbnail: Int,
    val pages: Int,
    val artists: List<String>?,
    val circles: List<String>?,
    val tags: List<String>?,
)

@Serializable
class ShortHentai(
    val hash: String,
    val thumbnail: Int,
    val description: String?,
    val released_at: String,
    val created_at: String,
    val publishers: List<String>?,
    val circles: List<String>?,
    val magazines: List<String>?,
    val parodies: List<String>?,
    val events: List<String>?,
    val size: Long,
    val pages: Int,
)

@Serializable
class Nodes(
    val nodes: List<Data>,
)

@Serializable
class Data(
    val data: List<JsonElement>,
)

@Serializable
class HentaiIndexes(
    val hash: Int,
    val thumbnail: Int,
    val description: Int,
    val released_at: Int,
    val created_at: Int,
    val publishers: Int,
    val circles: Int,
    val magazines: Int,
    val parodies: Int,
    val events: Int,
    val size: Int,
    val pages: Int,
)
