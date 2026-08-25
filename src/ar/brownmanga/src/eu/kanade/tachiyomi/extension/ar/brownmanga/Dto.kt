package eu.kanade.tachiyomi.extension.ar.brownmanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.get
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class ManhwaDto(
    val id: String,
    val slug: String,
    val title: String,
    @SerialName("title_ar") val titleAr: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val description: String? = null,
    @SerialName("description_ar") val descriptionAr: String? = null,
    val status: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val genres: List<NameDto>? = null,
    val chapters: List<ChapterDto> = emptyList(),
) {

    fun toSManga() = SManga.create().apply {
        url = id
        title = titleAr?.ifEmpty { null } ?: this@ManhwaDto.title
        thumbnail_url = coverUrl
        description = descriptionAr?.ifEmpty { null } ?: this@ManhwaDto.description
        author = this@ManhwaDto.author
        author = this@ManhwaDto.artist
        status = when (this@ManhwaDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = this@ManhwaDto.genres?.joinToString { it.name }
        memo = buildJsonObject {
            put("slug", slug)
        }
    }
}

@Serializable
class ChapterDto(
    val id: String,
    @SerialName("chapter_number") val number: JsonPrimitive,
    val title: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("translator_name") val translator: String? = null,
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        url = id
        name = buildString {
            append("الفصل $number")
            if (title != null && this.toString() != title) append(" - $title")
        }
        date_upload = Instant.tryParse(publishedAt)
        scanlator = translator
        memo = buildJsonObject {
            put("slug", slug)
            put("number", number)
        }
    }
}

@Serializable
class NameDto(
    val name: String,
)

@Serializable
class ChapterPageDto(
    @SerialName("image_url") val imageUrl: String,
)
