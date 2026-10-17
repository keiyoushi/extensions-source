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
    val tags: List<Name>? = null,
) {
    val fullTitle: String
        get() = buildFullTitle(title, tags)
}

@Serializable
class ShortHentai(
    val hash: String,
    val title: String,
    val thumbnail: Int,
    val description: String? = null,
    val releasedAt: String? = null,
    val createdAt: String? = null,
    val tags: List<Name>? = null,
    val size: Long,
    val pages: Int,
) {
    val fullTitle: String
        get() = buildFullTitle(title, tags)
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
    val title: Int,
    val thumbnail: Int,
    val description: Int,
    val releasedAt: Int,
    val createdAt: Int,
    val tags: Int,
    val size: Int,
    val pages: Int,
)

private fun buildFullTitle(
    title: String,
    tags: List<Name>?,
): String {
    val grouped = tags.orEmpty()
        .filter { it.name.isNotBlank() }
        .groupBy { it.namespace }

    val circle = grouped["circle"]?.joinToString(" & ") { it.name }
    val artist = grouped["artist"]?.joinToString(" & ") { it.name }
    val magazine = grouped["magazine"]?.joinToString(" & ") { it.name }

    return buildString {
        when {
            !circle.isNullOrBlank() -> {
                append("[$circle")
                if (!artist.isNullOrBlank()) append(" ($artist)")
                append("] ")
            }
            !artist.isNullOrBlank() -> append("[$artist] ")
        }

        append(title)

        if (!magazine.isNullOrBlank()) {
            append(" ($magazine)")
        }
    }
}
