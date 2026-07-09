package eu.kanade.tachiyomi.extension.ja.nicovideoseiga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import org.jsoup.Jsoup

@Serializable
class ApiResponse<T>(
    val data: Data<T>,
)

@Serializable
class Data<T>(
    @Serializable(with = SingleResultSerializer::class)
    val result: List<T>,
    val extra: Extra? = null,
)

@Serializable
class Extra(
    @SerialName("has_next")
    val hasNext: Boolean? = null,
)

class SingleResultSerializer<T>(serializer: KSerializer<T>) : JsonTransformingSerializer<List<T>>(ListSerializer(serializer)) {
    // Wrap single results in a list. Leave multiple results as is.
    override fun transformSerialize(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> element
        is JsonObject -> JsonArray(listOf(element))
        else -> throw IllegalStateException("Unexpected JSON element type: $element")
    }

    override fun transformDeserialize(element: JsonElement): JsonElement = element as? JsonArray ?: JsonArray(listOf(element))
}

// Result objects

@Serializable
class PopularManga(
    private val id: Int,
    private val title: String,
    private val author: String,
    @SerialName("thumbnail_url")
    private val thumbnailUrl: String,
) {
    fun toSManga() = SManga.create().apply {
        this.title = this@PopularManga.title
        this.author = this@PopularManga.author

        // The thumbnail provided only displays a glimpse of the latest chapter. Not the actual cover
        // We can obtain a better thumbnail when the user clicks into the details
        this.thumbnail_url = this@PopularManga.thumbnailUrl

        // Store id only as we override the url down the chain
        this.url = this@PopularManga.id.toString()
    }
}

@Serializable
class Manga(
    private val id: Int,
    private val meta: MangaMetadata,
) {
    @Serializable
    class MangaMetadata(
        val title: String,
        @SerialName("display_author_name")
        val author: String,
        val description: String,
        @SerialName("serial_status")
        val serialStatus: String,
        @SerialName("square_image_url")
        val thumbnailUrl: String,
    )

    fun toSManga() = SManga.create().apply {
        title = meta.title

        // The description is HTML. Simply using Jsoup to remove all the HTML tags
        description = Jsoup.parseBodyFragment(meta.description).wholeText()

        // Although their API does contain individual author fields, they are arbitrary strings, and we can't trust it conforms to a format
        // Use display name instead which puts all the people involved together
        author = meta.author

        thumbnail_url = meta.thumbnailUrl

        // Store id only as we override the url down the chain
        url = id.toString()

        status = when (meta.serialStatus) {
            "serial" -> SManga.ONGOING
            "concluded" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}

// Frames are the internal name for pages in the API
@Serializable
class Frame(
    val meta: FrameMetadata,
) {
    @Serializable
    class FrameMetadata(
        @SerialName("source_url")
        val sourceUrl: String,
    )
}

// Chapters are known as Episodes internally in the API
@Serializable
class Chapter(
    private val id: Int,
    private val meta: ChapterMetadata,
    @SerialName("own_status")
    val ownership: Ownership,
) {
    @Serializable
    class ChapterMetadata(
        val title: String,
        val number: Int,
        @SerialName("created_at")
        val createdAt: Long,
    )

    @Serializable
    class Ownership(
        @SerialName("sell_status")
        val sellStatus: String,
    )

    fun toSChapter() = SChapter.create().apply {
        val prefix = when (ownership.sellStatus) {
            "selling" -> "\uD83D\uDCB4 "
            "pre_selling" -> "\u23F3\uD83D\uDCB4 "
            else -> ""
        }
        name = prefix + meta.title

        // Timestamp is in seconds, convert to milliseconds
        date_upload = meta.createdAt * 1000

        // While chapters are properly sorted, authors often add promotional material as "chapters" which breaks trackers
        // There's no way to properly filter these as they are treated the same as normal chapters
        chapter_number = meta.number.toFloat()

        // Store id only as we override the url down the chain
        url = id.toString()
    }
}
