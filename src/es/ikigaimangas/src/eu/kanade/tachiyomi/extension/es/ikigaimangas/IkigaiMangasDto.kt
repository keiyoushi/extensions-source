package eu.kanade.tachiyomi.extension.es.ikigaimangas

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat

@Serializable
class QwikData(
    @SerialName("_objs") val objects: List<JsonElement>,
) {
    inline fun <reified T> parseAsList(): List<T> {
        val arr = objects
        val results = mutableListOf<T>()
        var i = 0

        while (i < arr.size) {
            var mapIndex = i
            while (mapIndex < arr.size && arr[mapIndex] !is JsonObject) {
                mapIndex++
            }
            if (mapIndex >= arr.size) break

            val map = arr[mapIndex].jsonObject

            val objContent = buildMap {
                for ((key, jsonIndex) in map) {
                    val ref = jsonIndex.jsonPrimitive.content

                    val index = try {
                        ref.toInt(radix = 36)
                    } catch (_: Exception) {
                        throw Exception("Invalid base36 index: $ref")
                    }

                    val rawValue = arr.getOrNull(index)

                    put(key, unwrapJson(rawValue))
                }
            }

            results.add(JsonObject(objContent).parseAs<T>())

            i = mapIndex + 1
        }

        return results
    }

    fun unwrapJson(el: JsonElement?): JsonElement = when (el) {
        is JsonPrimitive -> el
        is JsonObject -> el
        is JsonArray -> JsonArray(el.map { unwrapJson(it) })
        else -> JsonNull
    }
}

@Serializable
class PayloadLatestDto(
    val data: List<LatestDto>,
    @SerialName("current_page") private val currentPage: Int = 0,
    @SerialName("last_page") private val lastPage: Int = 0,
) {
    fun hasNextPage() = currentPage < lastPage
}

@Serializable
class LatestDto(
    @SerialName("series_id") private val id: Long,
    @SerialName("series_name") private val name: String,
    @SerialName("series_slug") private val slug: String,
    private val thumbnail: String? = null,
    val type: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/series/comic-$slug#$id"
        title = name
        thumbnail_url = thumbnail
    }
}

@Serializable
class PayloadSeriesDto(
    val data: List<SeriesDto>,
    @SerialName("current_page") private val currentPage: Int = 0,
    @SerialName("last_page") private val lastPage: Int = 0,
) {
    fun hasNextPage() = currentPage < lastPage
}

@Serializable
class QwikSeriesDto(
    private val id: Long,
    val name: String,
    private val slug: String,
    private val cover: String? = null,
    val type: String? = null,
    @SerialName("is_mature") val isMature: Boolean = false,
) {
    fun toSManga(imageCdnUrl: String) = SManga.create().apply {
        url = "/series/comic-$slug#$id"
        title = name
        thumbnail_url = cover?.let { "$imageCdnUrl/$it" }
    }
}

@Serializable
class SeriesDto(
    private val id: Long,
    private val name: String,
    private val slug: String,
    private val cover: String? = null,
    val type: String? = null,
    private val summary: String? = null,
    private val status: SeriesStatusDto? = null,
    private val genres: List<FilterDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/series/comic-$slug#$id"
        title = name
        thumbnail_url = cover
    }

    fun toSMangaDetails() = SManga.create().apply {
        title = name
        thumbnail_url = cover
        description = summary
        status = parseStatus(this@SeriesDto.status?.id)
        genre = genres?.joinToString { it.name.trim() }
    }

    private fun parseStatus(statusId: Long?) = when (statusId) {
        906397890812182531, 911437469204086787 -> SManga.ONGOING
        906409397258190851 -> SManga.ON_HIATUS
        906409532796731395, 911793517664960513 -> SManga.COMPLETED
        906426661911756802, 906428048651190273, 911793767845265410, 911793856861798402 -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class PayloadSeriesDetailsDto(
    val series: SeriesDto,
)

@Serializable
class PayloadChaptersDto(
    val data: List<ChapterDto>,
    val meta: ChapterMetaDto,
)

@Serializable
class ChapterDto(
    private val id: Long,
    private val name: String,
    private val title: String? = null,
    @SerialName("published_at") val date: String,
) {
    fun toSChapter(dateFormat: SimpleDateFormat) = SChapter.create().apply {
        url = "/capitulo/$id/"
        name = buildString {
            append("Capítulo ${this@ChapterDto.name}")
            title?.let {
                append(": $it")
            }
        }
        date_upload = try {
            dateFormat.parse(date)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}

@Serializable
class ChapterMetaDto(
    @SerialName("current_page") private val currentPage: Int,
    @SerialName("last_page") private val lastPage: Int,
) {
    fun hasNextPage() = currentPage < lastPage
}

@Serializable
class SeriesStatusDto(
    val id: Long,
)

@Serializable
class PayloadFiltersDto(
    val data: GenresStatusesDto,
)

@Serializable
class GenresStatusesDto(
    val genres: List<FilterDto>,
    val statuses: List<FilterDto>,
)

@Serializable
class FilterDto(
    val id: Long,
    val name: String,
)
