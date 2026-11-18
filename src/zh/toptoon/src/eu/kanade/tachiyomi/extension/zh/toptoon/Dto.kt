package eu.kanade.tachiyomi.extension.zh.toptoon

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.getString
import keiyoushi.utils.string
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
class PopularResponseDto(val adult: List<MangaDto>)

@Serializable
class MangaDto(
    private val meta: MetaDto,
    private val thumbnail: ThumbnailDto,
    private val id: String,
    val lastUpdated: JsonElement,
) {
    // can be empty array or object
    val pubDate: String
        get() = when (lastUpdated) {
            is JsonObject -> lastUpdated.getString("pubDate")
            else -> "0"
        }

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
    val url get() = "https://tw-contents-image.toptoon.net" + (if (standard is JsonArray) standard[0] else standard).string
}
