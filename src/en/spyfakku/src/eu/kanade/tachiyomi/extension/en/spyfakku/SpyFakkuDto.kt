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
    val publishers: List<String>?,
    val artists: List<String>?,
    val circles: List<String>?,
    val magazines: List<String>?,
    val parodies: List<String>?,
    val events: List<String>?,
    val tags: List<String>?,
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
    val id: Int,
    val description: Int,
    val released_at: Int,
    val created_at: Int,
    val size: Int,
)
