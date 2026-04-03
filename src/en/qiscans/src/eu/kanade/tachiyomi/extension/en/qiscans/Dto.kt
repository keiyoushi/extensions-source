package eu.kanade.tachiyomi.extension.en.qiscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class SearchResponse(
    val data: List<Manga>,
    val totalItems: Int,
    val totalPages: Int,

    @SerialName("current") val currentPage: Int,
    @SerialName("next") val nextPage: Int?,
)

@Serializable
class Manga(
    val slug: String,
    val title: String,
    val cover: String,
    val status: String,
    val type: String,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@Manga.title
        thumbnail_url = cover
        status = when (this@Manga.status) {
            "ONGOING", "MASS_RELEASED" -> SManga.ONGOING
            "COMPLETED" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}

@Serializable
class ChapterList(
    val data: List<Chapter>,
    val totalItems: Int,
    val totalPages: Int,
    @SerialName("current") val currentPage: Int,
    @SerialName("next") val nextPage: Int?,

)

@Serializable
class Chapter(
    val id: Int,
    val slug: String,
    val number: JsonPrimitive,
    val isFree: Boolean,
    val createdAt: String,
    val publishStatus: String,
) {
    fun isPublic() = publishStatus == "PUBLIC"

    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "series/$mangaSlug/chapters/$slug"
        name = "Chapter $number"
        date_upload = dateFormat.tryParse(createdAt)
    }
}

@Serializable
class Images(
    val images: List<ParsePageDto>,
    val id: Int? = null,
)

@Serializable
class ParsePageDto(
    val url: String,
    val order: Int? = null,
)
private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
