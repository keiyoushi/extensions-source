package eu.kanade.tachiyomi.extension.en.dynasty

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
) {
    val directory get() = when (type) {
        SERIES_TYPE -> SERIES_DIR
        ANTHOLOGY_TYPE -> ANTHOLOGIES_DIR
        DOUJIN_TYPE -> DOUJINS_DIR
        ISSUE_TYPE -> ISSUES_DIR
        else -> throw Exception("Unsupported Type for directory: $type")
    }
}

@Serializable
class TagSuggest(
    val id: Int,
    val name: String,
    val type: String,
)

class MangaEntry(
    private val title: String,
    val url: String,
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
class MangaResponse(
    val name: String,
    val type: String,
    val tags: List<BrowseTag>,
    val cover: String?,
    val description: String?,
    val aliases: List<String>,
    @Serializable(with = ChapterItemListSerializer::class)
    val taggings: List<ChapterItem>,
    @SerialName("total_pages") val totalPages: Int = 0,
)

@Serializable
sealed class ChapterItem

@Serializable
@SerialName("header")
class MangaChapterHeader(
    val header: String?,
) : ChapterItem()

@Serializable
@SerialName("chapter")
class MangaChapter(
    val title: String,
    val permalink: String,
    @SerialName("released_on") val releasedOn: String,
    val tags: List<BrowseTag>,
) : ChapterItem()

object ChapterItemListSerializer : JsonTransformingSerializer<List<ChapterItem>>(ListSerializer(ChapterItem.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return JsonArray(
            element.jsonArray.map { jsonElement ->
                val jsonObject = jsonElement.jsonObject
                when {
                    "header" in jsonObject -> JsonObject(
                        jsonObject + ("type" to JsonPrimitive("header")),
                    )
                    else -> JsonObject(
                        jsonObject + ("type" to JsonPrimitive("chapter")),
                    )
                }
            },
        )
    }
}

@Serializable
class ChapterResponse(
    val title: String,
    val tags: List<BrowseTag>,
    val pages: List<Page>,
    @SerialName("released_on") val releasedOn: String,
)

@Serializable
class Page(
    val url: String,
)
