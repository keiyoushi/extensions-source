package eu.kanade.tachiyomi.extension.en.dynasty

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Serializable
class BrowseResponse(
    val chapters: List<BrowseChapter>,
    @SerialName("current_page") private val currentPage: Int,
    @SerialName("total_pages") private val totalPages: Int,
) {
    fun hasNextPage() = currentPage <= totalPages
}

@Serializable
class BrowseChapter(
    val title: String,
    val permalink: String,
    val tags: List<BrowseTag>,
)

@Serializable
class BrowseTag(
    val type: String,
    val name: String,
    val permalink: String,
    val cover: String? = null,
) {
    val directory get() = when (type) {
        "Series" -> "series"
        "Anthology" -> "anthologies"
        "Doujin" -> "doujins"
        "Issue" -> "issues"
        else -> null
    }
}

class MangaEntry(
    private val title: String,
    private val url: String,
    private val cover: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = this@MangaEntry.url
        title = this@MangaEntry.title
        thumbnail_url = cover
    }

    override fun equals(other: Any?): Boolean {
        return this.url == (other as MangaEntry?)?.url
    }

    override fun hashCode(): Int {
        return this.url.hashCode()
    }
}

@Serializable
class AuthorResponse(
    val taggables: List<BrowseTag>,
    val taggings: List<BrowseChapter>,
)

@Serializable
class ScanlatorResponse(
    val taggings: List<BrowseChapter>,
    @SerialName("current_page") private val currentPage: Int,
    @SerialName("total_pages") private val totalPages: Int,
) {
    fun hasNextPage() = currentPage <= totalPages
}

class MangaResponse(
    val name: String,
    val tags: List<BrowseTag>,
    val cover: String?,
    val description: String?,
    val aliases: List<String>,
    @Serializable(with = NonHeaderTaggingsSerializer::class)
    val taggings: List<BrowseChapter>
)

object NonHeaderTaggingsSerializer : JsonTransformingSerializer<List<BrowseChapter>>(ListSerializer(BrowseChapter.serializer())) {
    override fun transformSerialize(element: JsonElement): JsonElement {
        if (element !is JsonArray) return element

        val filteredArray = JsonArray(element.filterIsInstance<JsonObject>().filter { jsonObject ->
            jsonObject.keys.size > 1 || !jsonObject.containsKey("header")
        })

        return filteredArray
    }
}

@Serializable
class ChapterResponse(
    val pages: List<Page>,
)

@Serializable
class Page(
    val url: String,
)
