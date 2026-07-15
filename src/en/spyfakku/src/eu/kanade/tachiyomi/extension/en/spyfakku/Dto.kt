package eu.kanade.tachiyomi.extension.en.spyfakku

import kotlinx.serialization.SerialName
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
    val tags: List<Name>? = null,
)

@Serializable
class ShortHentai(
    val hash: String,
    val thumbnail: Int,
    val description: String? = null,
    @SerialName("released_at") val releasedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val tags: List<Name>? = null,
    val size: Long,
    val pages: Int,
)

@Serializable
class Name(
    val namespace: String,
    val name: String,
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
    @SerialName("released_at") val releasedAt: Int,
    @SerialName("created_at") val createdAt: Int,
    val tags: Int,
    val size: Int,
    val pages: Int,
)
