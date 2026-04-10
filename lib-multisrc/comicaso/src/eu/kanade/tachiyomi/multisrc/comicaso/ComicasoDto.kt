package eu.kanade.tachiyomi.multisrc.comicaso

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.serializer

object ThumbnailSerializer : JsonTransformingSerializer<String?>(serializer<String?>()) {
    override fun transformDeserialize(element: JsonElement): JsonElement = when {
        element is JsonNull -> JsonNull
        element is JsonPrimitive && !element.isString -> JsonNull
        else -> element
    }
}

@Serializable
class MangaDto(
    val slug: String,
    val title: String,
    @Serializable(ThumbnailSerializer::class)
    val thumbnail: String? = null,
    val status: String? = null,
    val type: String? = null,
    val genres: List<String>? = emptyList(),
    @SerialName("manga_date") val mangaDate: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaDto.title
        thumbnail_url = thumbnail
        genre = genres?.joinToString()
        status = when (this@MangaDto.status) {
            "on-going" -> SManga.ONGOING
            "end" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class MangaDetailDto(
    val slug: String,
    val title: String,
    @Serializable(ThumbnailSerializer::class)
    val thumbnail: String? = null,
    val synopsis: String? = null,
    val alternative: String? = null,
    val status: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val genres: List<String>? = emptyList(),
    val chapters: List<ChapterDto>? = emptyList(),
)

@Serializable
class ChapterDto(
    val slug: String,
    val title: String,
    val date: Long? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/komik/$mangaSlug/$slug/"
        name = title
        date_upload = date?.let { it * 1000L } ?: 0L
    }
}
