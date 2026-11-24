package eu.kanade.tachiyomi.extension.en.bakaloli

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
class ListRequestDto(
    private val action: String,
    private val items: ListItemsDto,
)

@Serializable
class ListItemsDto(
    private val href: String,
    private val what: Int,
)

@Serializable
class ListDto(
    val items: List<ItemDto>,
)

@Serializable
class ItemDto(
    val href: String,
    val lastModified: Long,
    private val time: Long,
    private val thumbSquare: String? = null,
    private val tags: TagsDto? = null,
) {
    val isFolder get() = href.endsWith("/")

    fun name(baseUrl: String) = (baseUrl + href).toHttpUrl().pathSegments.last { it.isNotEmpty() }

    fun number(baseUrl: String) = NUMBER_REGEX.find(name(baseUrl))?.value?.toFloat()

    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = href
        title = name(baseUrl)
        thumbnail_url = thumbSquare?.let { baseUrl + it }
        author = tags?.author?.names()
        artist = tags?.artist?.names()
        genre = tags?.type
        description = tags?.description?.takeIf { it.isNotBlank() }
        status = when (tags?.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    fun toSChapter(baseUrl: String) = SChapter.create().apply {
        url = href
        name = name(baseUrl)
        chapter_number = number(baseUrl) ?: -1f
        date_upload = time
    }

    // Authors are either one name or a list of them
    private fun JsonElement.names() = (this as? JsonArray)?.joinToString { it.jsonPrimitive.content } ?: jsonPrimitive.content

    companion object {
        private val NUMBER_REGEX = Regex("""\d+(?:\.\d+)?""")
    }
}

@Serializable
class TagsDto(
    val type: String? = null,
    val author: JsonElement? = null,
    val artist: JsonElement? = null,
    val status: String? = null,
    val description: String? = null,
)
