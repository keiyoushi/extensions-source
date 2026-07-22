package eu.kanade.tachiyomi.extension.pt.mangalivre

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.text.Normalizer

class WrapperDto(
    val mangas: List<MangaDto>,
    val pagination: PaginationDto,
) {
    val hasNextPage get() = pagination.hasNextPage

    companion object {
        fun fromJson(json: JsonObject) = WrapperDto(
            mangas = json.requiredArray("mangas").mapObjects(MangaDto::fromJson),
            pagination = PaginationDto.fromJson(json.requiredObject("pagination")),
        )
    }
}

class PaginationDto(
    val hasNextPage: Boolean,
) {
    companion object {
        fun fromJson(json: JsonObject) = PaginationDto(json.requiredBoolean("hasNextPage"))
    }
}

class MangaDto(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val authors: List<String>? = null,
    val artists: List<String>? = null,
    val genres: List<String>? = null,
    val description: String? = null,
    val alternativeTitle: String? = null,
    val chapters: List<ChapterDto>? = null,
    val status: String? = null,
) {
    fun toSManga(useAlternativeTitle: Boolean) = SManga.create().apply {
        this.title = if (useAlternativeTitle && !alternativeTitle.isNullOrBlank()) alternativeTitle else this@MangaDto.title
        this.thumbnail_url = this@MangaDto.thumbnailUrl
        this.description = buildString {
            if (!this@MangaDto.description.isNullOrBlank()) {
                append(this@MangaDto.description)
            }

            if (!alternativeTitle.isNullOrBlank()) {
                appendLine("${"\n".repeat(3)} Nome alternativo: $alternativeTitle")
            }
        }
        author = authors?.joinToString()
        artist = artists?.joinToString()
        genre = genres?.joinToString()
        this@MangaDto.status?.let {
            status = when (it.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
        this.url = id
    }

    fun toSChapterList(): List<SChapter> = chapters?.map { it.toSChapter(getSlug(), id) } ?: emptyList()

    private fun getSlug(): String {
        val noDiacritics = Normalizer.normalize(title, Normalizer.Form.NFD)
            .replace(MARKS_REGEX, "")
        return noDiacritics.lowercase()
            .replace(NON_ALPHA_REGEX, "-")
            .trim('-')
    }

    companion object {
        private val MARKS_REGEX = "\\p{InCombiningDiacriticalMarks}+".toRegex()
        private val NON_ALPHA_REGEX = "[^a-z0-9]+".toRegex()

        fun fromJson(json: JsonObject) = MangaDto(
            id = json.requiredString("id"),
            title = json.requiredString("title"),
            thumbnailUrl = json.optionalString("coverUrl"),
            authors = json.optionalStringList("authors"),
            artists = json.optionalStringList("artists"),
            genres = json.optionalStringList("genres"),
            description = json.optionalString("description"),
            alternativeTitle = json.optionalString("alternativeTitle"),
            chapters = json["chapters"]?.takeUnless { it === JsonNull }
                ?.jsonArray
                ?.mapObjects(ChapterDto::fromJson),
            status = json.optionalString("status"),
        )
    }
}

class ChapterReferenceDto(
    val mangaId: String,
    val chapterId: String,
) {
    fun toJson(): String = buildJsonObject {
        put("mangaId", mangaId)
        put("chapterId", chapterId)
    }.toString()

    companion object {
        fun fromJson(value: String) = Json.parseToJsonElement(value).jsonObject.let {
            ChapterReferenceDto(it.requiredString("mangaId"), it.requiredString("chapterId"))
        }
    }
}

class ChapterDto(
    val id: String,
    val number: String,
    val timestamp: Long,
) {
    fun toSChapter(slug: String, mangaId: String) = SChapter.create().apply {
        name = "Capítulo $number"
        date_upload = timestamp
        url = "/$slug/$number#${ChapterReferenceDto(mangaId, id).toJson()}"
    }

    companion object {
        fun fromJson(json: JsonObject) = ChapterDto(
            id = json.requiredString("id"),
            number = json.requiredString("number"),
            timestamp = json.requiredLong("timestamp"),
        )
    }
}

class PageDto(
    val pages: List<String>,
) {
    fun toPageList() = pages.mapIndexed { index, imageUrl ->
        Page(index, imageUrl = imageUrl)
    }

    companion object {
        fun fromJson(json: JsonObject) = PageDto(json.requiredArray("pages").toStringList())
    }
}

private fun JsonObject.requiredElement(key: String) = this[key]
    ?: throw SerializationException("Missing required field: $key")

private fun JsonObject.requiredString(key: String): String = requiredElement(key).jsonPrimitive.content

private fun JsonObject.requiredBoolean(key: String): Boolean = requiredElement(key).jsonPrimitive.booleanOrNull
    ?: throw SerializationException("Field is not a boolean: $key")

private fun JsonObject.requiredLong(key: String): Long = requiredElement(key).jsonPrimitive.longOrNull
    ?: throw SerializationException("Field is not a long: $key")

private fun JsonObject.requiredArray(key: String): JsonArray = requiredElement(key).jsonArray

private fun JsonObject.requiredObject(key: String): JsonObject = requiredElement(key).jsonObject

private fun JsonObject.optionalString(key: String): String? = this[key]
    ?.jsonPrimitive
    ?.contentOrNull
    ?.takeIf { it.isNotBlank() }

private fun JsonObject.optionalStringList(key: String): List<String>? = this[key]
    ?.takeUnless { it === JsonNull }
    ?.jsonArray
    ?.toStringList()

private fun JsonArray.toStringList(): List<String> = map { it.jsonPrimitive.content }

private fun <T> JsonArray.mapObjects(transform: (JsonObject) -> T): List<T> = map { transform(it.jsonObject) }
