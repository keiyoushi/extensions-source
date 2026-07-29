package eu.kanade.tachiyomi.extension.es.olympusscanlation

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.text.ParseException
import java.text.SimpleDateFormat

@Serializable
class PayloadHomeDto(
    val data: HomeDto,
)

@Serializable
class HomeDto(
    @SerialName("popular_comics")
    @Serializable(with = PopularComicsSerializer::class)
    val popularComics: List<MangaDto>,
)

object PopularComicsSerializer : JsonTransformingSerializer<List<MangaDto>>(ListSerializer(MangaDto.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement = if (element is JsonPrimitive && element.isString) {
        Json.parseToJsonElement(element.content)
    } else {
        element
    }
}

@Serializable
class PayloadSeriesDto(
    val data: PayloadSeriesDataDto,
)

@Serializable
class PayloadSeriesDataDto(
    val series: SeriesDto? = null, // Can be object sometimes
    val current_page: Int? = null,
    @Serializable(with = FlexibleMangaListNullableSerializer::class)
    val data: List<MangaDto>? = null,
    val last_page: Int? = null,
)

@Serializable
class SeriesDto(
    val current_page: Int,
    @Serializable(with = FlexibleMangaListSerializer::class)
    val data: List<MangaDto>,
    val last_page: Int,
) {
    fun hasNextPage() = current_page < last_page
}

@Serializable
class HomepageDto(
    val data: HomepageDataDto,
    val rankings: List<HomepageMangaDto>? = null,
)

@Serializable
class HomepageDataDto(
    @SerialName("new_chapters") val newChapters: List<HomepageMangaDto>? = null,
)

@Serializable
class HomepageMangaDto(
    val id: Int,
    val slug: String,
    val type: String? = null,
)

@Serializable
class PayloadMangaDto(
    @Serializable(with = FlexibleMangaListSerializer::class)
    val data: List<MangaDto>,
)

@Serializable
class MangaDto(
    val id: Int? = null,
    val name: String,
    val slug: String,
    private val cover: String? = null,
    val type: String? = null,
    private val summary: String? = null,
    private val status: MangaStatusDto? = null,
    private val genres: List<FilterDto>? = null,
) {
    fun toSManga(resolvedId: String? = id?.toString()) = SManga.create().apply {
        title = name.trim()
        val cleanSlug = slug.trim().removeSuffix("/")
        url = resolvedId ?: "/series/comic-$cleanSlug"
        thumbnail_url = cover?.trim()
    }

    fun toSMangaDetails(resolvedId: String? = id?.toString()) = toSManga(resolvedId).apply {
        description = summary
        status = parseStatus()
        genre = genres?.joinToString { it.name.trim() }
    }

    private fun parseStatus(): Int {
        val status = this.status ?: return SManga.UNKNOWN
        return when (status.id) {
            1 -> SManga.ONGOING
            3 -> SManga.ON_HIATUS
            4 -> SManga.COMPLETED
            5 -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class NewChaptersDto(
    val data: List<LatestMangaDto>,
    val current_page: Int,
    val last_page: Int,
)

@Serializable
class LatestMangaDto(
    val id: Int? = null,
    val name: String,
    val slug: String,
    private val cover: String? = null,
    val type: String? = null,
) {
    fun toSManga(resolvedId: String? = id?.toString()) = SManga.create().apply {
        title = name.trim()
        val cleanSlug = slug.trim().removeSuffix("/")
        url = resolvedId ?: "/series/comic-$cleanSlug"
        thumbnail_url = cover?.trim()
    }
}

@Serializable
class MangaDetailDto(
    @Serializable(with = FlexibleMangaDataSerializer::class)
    var data: MangaDto,
)

object FlexibleMangaDataSerializer : JsonTransformingSerializer<MangaDto>(MangaDto.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement = if (element is kotlinx.serialization.json.JsonArray) {
        // Si data es un array, tomar el primer elemento
        element.firstOrNull() ?: throw kotlinx.serialization.SerializationException("Empty array in data field")
    } else {
        // Si data es un objeto, usarlo directamente
        element
    }
}

object FlexibleMangaListSerializer : JsonTransformingSerializer<List<MangaDto>>(ListSerializer(MangaDto.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement = if (element is kotlinx.serialization.json.JsonObject) {
        // Si data es un objeto, envolverlo en un array
        kotlinx.serialization.json.JsonArray(listOf(element))
    } else {
        // Si data ya es un array, usarlo directamente
        element
    }
}

object FlexibleMangaListNullableSerializer : JsonTransformingSerializer<List<MangaDto>>(ListSerializer(MangaDto.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement = if (element is kotlinx.serialization.json.JsonObject) {
        kotlinx.serialization.json.JsonArray(listOf(element))
    } else if (element is kotlinx.serialization.json.JsonNull) {
        kotlinx.serialization.json.JsonArray(emptyList())
    } else {
        element
    }
}

fun Json.decodeMangaListPayload(body: String): List<MangaDto> {
    val root = parseToJsonElement(body).jsonObject
    val data = root["data"]
    return when (data) {
        is JsonArray -> decodeFromJsonElement(ListSerializer(MangaDto.serializer()), data)
        is JsonObject -> {
            // La API /api/series devuelve: {"data": {"series": {"data": [...]}}, "recommended_series": "..."}
            if (data.containsKey("series")) {
                val payload = decodeFromJsonElement<PayloadSeriesDto>(root)
                val seriesData = payload.data.series ?: SeriesDto(
                    current_page = payload.data.current_page ?: 1,
                    data = payload.data.data ?: emptyList(),
                    last_page = payload.data.last_page ?: 1,
                )
                seriesData.data
            } else {
                listOf(decodeFromJsonElement(MangaDto.serializer(), data))
            }
        }
        null -> decodeFromString<PayloadMangaDto>(body).data
        else -> emptyList()
    }
}

fun Json.decodeMangaDetailPayload(body: String): MangaDto {
    val data = parseToJsonElement(body).jsonObject["data"]
    return when (data) {
        is JsonArray -> data.firstOrNull()?.let { decodeFromJsonElement(MangaDto.serializer(), it) }
            ?: throw kotlinx.serialization.SerializationException("Empty array in data field")
        is JsonObject -> decodeFromJsonElement(MangaDto.serializer(), data)
        null -> decodeFromString<MangaDetailDto>(body).data
        else -> throw kotlinx.serialization.SerializationException("Unsupported data field type")
    }
}

@Serializable
class PayloadChapterDto(
    var data: List<ChapterDto>,
    val meta: MetaDto,
)

@Serializable
class ChapterDto(
    internal val id: Int,
    internal val name: String,
    @SerialName("published_at") private val date: String,
) {
    fun toSChapter(
        mangaSlug: String,
        dateFormat: SimpleDateFormat,
        mangaId: String? = null,
    ) = SChapter.create().apply {
        name = "Capitulo ${this@ChapterDto.name}"
        chapter_number = this@ChapterDto.name.toFloatOrNull() ?: -1f
        // URL con slug actual + ID para rastreo cuando el slug cambie
        url =
            if (mangaId != null) {
                "/capitulo/$id/comic-$mangaSlug?mangaId=$mangaId"
            } else {
                "/capitulo/$id/comic-$mangaSlug"
            }
        date_upload =
            try {
                dateFormat.parse(date)!!.time
            } catch (e: ParseException) {
                0L
            }
    }
}

@Serializable
class MetaDto(
    val total: Int,
)

@Serializable
class PayloadPagesDto(
    val chapter: PageDto,
)

@Serializable
class PageDto(
    val pages: List<String>,
)

@Serializable
class MangaStatusDto(
    val id: Int,
)

@Serializable
class GenresStatusesDto(
    val genres: List<FilterDto>,
    val statuses: List<FilterDto>,
)

@Serializable
class FilterDto(
    val id: Int,
    val name: String,
)
