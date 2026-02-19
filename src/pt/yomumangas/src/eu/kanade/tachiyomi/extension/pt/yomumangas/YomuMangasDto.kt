package eu.kanade.tachiyomi.extension.pt.yomumangas

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class YomuMangasHomeDto(
    val updates: List<YomuMangasSeriesDto> = emptyList(),
    val votes: List<YomuMangasSeriesDto> = emptyList(),
)

@Serializable
data class YomuMangasSearchDto(
    val mangas: List<YomuMangasSeriesDto> = emptyList(),
    val page: Int,
    val pages: Int,
) {

    val hasNextPage: Boolean
        get() = page < pages
}

@Serializable
data class YomuMangasDetailsDto(val manga: YomuMangasSeriesDto)

@Serializable
data class YomuMangasSeriesDto(
    val id: Int,
    val slug: String,
    val title: String,
    val cover: String? = null,
    val status: String,
    val authors: List<String>? = emptyList(),
    val artists: List<String>? = emptyList(),
    @Serializable(with = YomuMangasGenreDtoSerializer::class)
    val genres: List<YomuMangasGenreDto>? = emptyList(),
    val description: String? = null,
) {

    val genre: String?
        get() = genres
            ?.filter { it.name.equals("unknown").not() }
            ?.joinToString { it.name }

    fun toSManga(): SManga = SManga.create().apply {
        title = this@YomuMangasSeriesDto.title
        author = authors.orEmpty().joinToString { it.trim() }
        artist = artists.orEmpty().joinToString { it.trim() }
        genre = this@YomuMangasSeriesDto.genre
        description = this@YomuMangasSeriesDto.description?.trim()
        status = when (this@YomuMangasSeriesDto.status) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETE" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "CANCELLED" -> SManga.CANCELLED
            "PLANNED" -> SManga.PUBLISHING_FINISHED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = cover?.let { "${YomuMangas.CDN_URL}/images/${it.substringAfter("//")}" }
        url = "/mangas/$id/$slug"
    }
}

@Serializable
data class YomuMangasGenreDto(val name: String)

private object YomuMangasGenreDtoSerializer : JsonTransformingSerializer<List<YomuMangasGenreDto>>(
    ListSerializer(YomuMangasGenreDto.serializer()),
) {
    override fun transformDeserialize(element: JsonElement): JsonElement = JsonArray(
        element.jsonArray.map { jsonElement ->
            jsonElement.takeIf { it.isObject } ?: buildJsonObject {
                genresList.firstOrNull { it.id.equals(jsonElement.jsonPrimitive.intOrNull) }?.let {
                    put("name", JsonPrimitive(it.name))
                } ?: put("name", JsonPrimitive("unknown"))
            }
        },
    )

    private val JsonElement.isObject get() = this is JsonObject
}

@Serializable
data class YomuMangasChaptersDto(val chapters: List<YomuMangasChapterDto> = emptyList())

@Serializable
data class YomuMangasChapterDto(
    val id: Int,
    val chapter: Float,
    @SerialName("uploaded_at") val uploadedAt: String,
    val images: List<YomuMangasImageDto>? = emptyList(),
) {

    fun toSChapter(series: YomuMangasSeriesDto): SChapter = SChapter.create().apply {
        name = "Capítulo ${chapter.toString().removeSuffix(".0")}"
        date_upload = DATE_FORMATTER.tryParse(uploadedAt)
        url = "/mangas/${series.id}/${series.slug}/$chapter"
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        }
    }
}

@Serializable
data class YomuMangasImageDto(val uri: String)
