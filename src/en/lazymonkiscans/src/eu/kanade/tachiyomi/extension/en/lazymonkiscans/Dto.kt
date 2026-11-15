package eu.kanade.tachiyomi.extension.en.lazymonkiscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

@Serializable
class ListDto(
    val documents: List<DocumentDto> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
class DocumentDto(
    private val name: String,
    private val fields: Map<String, ValueDto> = emptyMap(),
) {
    val id: String get() = name.substringAfterLast('/')

    fun text(field: String): String? = fields[field]?.text?.takeIf { it.isNotBlank() }

    fun list(field: String): List<ValueDto> = fields[field]?.arrayValue?.values.orEmpty()

    val views: Long get() = text("views")?.toLongOrNull() ?: 0L

    val createdAt: Long get() = Instant.tryParse(text("createdAt"))
}

// A Firestore value: numbers can be stored as integers, doubles (NaN as a string) or strings
@Serializable
class ValueDto(
    private val stringValue: String? = null,
    private val integerValue: String? = null,
    private val doubleValue: JsonPrimitive? = null,
    private val timestampValue: String? = null,
    val arrayValue: ArrayDto? = null,
    val mapValue: MapDto? = null,
) {
    val text: String? get() = stringValue ?: integerValue ?: doubleValue?.content ?: timestampValue
}

@Serializable
class ArrayDto(val values: List<ValueDto> = emptyList())

@Serializable
class MapDto(val fields: Map<String, ValueDto> = emptyMap())

@Serializable
class AtHomeDto(
    private val baseUrl: String,
    private val chapter: AtHomeChapterDto,
) {
    val imageUrls: List<String> get() = chapter.data.map { "$baseUrl/data/${chapter.hash}/$it" }
}

@Serializable
class AtHomeChapterDto(
    val hash: String,
    val data: List<String>,
)

fun DocumentDto.toSManga(coverUrl: String?) = SManga.create().apply {
    url = "/?view=manga&manga=$id"
    title = text("title")!!
    thumbnail_url = coverUrl
}

fun DocumentDto.toSMangaDetails(coverUrl: String?) = toSManga(coverUrl).apply {
    val ratings = text("ratingCount")?.toIntOrNull() ?: 0
    description = MarkdownDescription.Builder()
        .rating(text("ratingAvg")?.toDoubleOrNull()?.takeIf { ratings > 0 }, max = 5, votes = ratings)
        .description(text("description"))
        .build()
    genre = list("genres").mapNotNull { it.text }.joinToString().ifEmpty { null }
    status = when (text("status")?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }
}

fun DocumentDto.toSChapter(mangaId: String) = SChapter.create().apply {
    // A one-shot can have no number (NaN)
    val number = listOf(text("number"), text("chapterNumber")).firstOrNull { it?.toFloatOrNull()?.isFinite() == true }
    url = "/?view=reader&manga=$mangaId&chapter=$id"
    name = listOfNotNull(number?.let { "Chapter $it" }, text("title")).joinToString(": ").ifEmpty { "Chapter" }
    chapter_number = number?.toFloat() ?: -1f
    date_upload = createdAt
    scanlator = if (text("type") == "mtl") "MTL" else null
}
