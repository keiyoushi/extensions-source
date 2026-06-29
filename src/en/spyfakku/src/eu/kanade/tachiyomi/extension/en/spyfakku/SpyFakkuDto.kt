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
    val tags: List<Name>?,
)

@Serializable
class ShortHentai(
    val hash: String,
    val thumbnail: Int,
    val description: String?,
    val released_at: String? = null,
    val created_at: String? = null,
    var releasedAt: String? = null,
    var createdAt: String? = null,
    val tags: List<Name>?,
    val size: Long,
    val pages: Int,
) {
    init {
        releasedAt = released_at ?: releasedAt
        createdAt = created_at ?: createdAt
    }
}

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
    val released_at: Int,
    val created_at: Int,
    val tags: Int,
    val size: Int,
    val pages: Int,
)
