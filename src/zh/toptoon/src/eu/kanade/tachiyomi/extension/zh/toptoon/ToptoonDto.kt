package eu.kanade.tachiyomi.extension.zh.toptoon

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

@Serializable
class PopularResponseDto(val adult: List<MangaDto>)

@Serializable
class MangaDto(
    private val meta: MetaDto,
    private val thumbnail: ThumbnailDto,
    private val id: String,
    val lastUpdated: LastUpdatedDto,
) {
    fun toSManga() = SManga.create().apply {
        url = "/comic/epList/$id"
        title = meta.title
        author = meta.author.authorString
        thumbnail_url = thumbnail.url
    }
}

@Serializable
class MetaDto(val title: String, val author: AuthorDto)

@Serializable
class AuthorDto(val authorString: String)

@Serializable
class ThumbnailDto(private val standard: JsonElement) {
    // "standard" in json can be either string or array
    val url get() = when (standard) {
        is JsonPrimitive -> "https://tw-contents-image.toptoon.net${standard.content}"
        is JsonArray -> "https://tw-contents-image.toptoon.net${standard[0].jsonPrimitive.content}"
        else -> throw Exception("Unexpected JSON type")
    }
}

@Serializable
class LastUpdatedDto(val pubDate: String)
