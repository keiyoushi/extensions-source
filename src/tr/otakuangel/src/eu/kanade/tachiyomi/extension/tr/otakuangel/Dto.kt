package eu.kanade.tachiyomi.extension.tr.otakuangel

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import kotlin.time.Instant

const val CDN_URL = "https://angel.cdnotakuangel.com"

@Serializable
class SvelteLineDto(
    val type: String,
    val nodes: List<SvelteNodeDto?>? = null,
    val id: Int? = null,
    val data: JsonArray? = null,
)

@Serializable
class SvelteNodeDto(
    val type: String,
    val data: JsonArray? = null,
)

// SvelteKit's __data.json: a line with the data of the route's nodes, then a line per deferred value, all flattened by devalue
fun Response.parseSvelteData(): JsonElement? {
    val lines = use { it.body.string() }.lines().filter { it.isNotBlank() }.map { it.parseAs<SvelteLineDto>() }
    val chunks = lines.filter { it.type == "chunk" }.associate { it.id!! to it.data!! }
    val page = lines.first().nodes!!.last()
    if (page?.type != "data") return null
    return page.data!!.unflatten(chunks)
}

private fun JsonArray.unflatten(chunks: Map<Int, JsonArray>): JsonElement {
    fun resolve(index: Int): JsonElement {
        // Negative indexes stand for undefined, NaN and the infinities
        if (index < 0) return JsonNull
        val value = this[index]
        val tag = (value as? JsonArray)?.firstOrNull()?.jsonPrimitive?.takeIf { it.isString }?.content
        return when {
            tag == "Date" -> (value as JsonArray)[1]
            tag == "Promise" -> chunks[resolve((value as JsonArray)[1].jsonPrimitive.int).jsonPrimitive.int]?.unflatten(chunks) ?: JsonNull
            tag != null -> JsonNull
            value is JsonArray -> JsonArray(value.map { resolve(it.jsonPrimitive.int) })
            value is JsonObject -> JsonObject(value.mapValues { resolve(it.value.jsonPrimitive.int) })
            else -> value
        }
    }
    return resolve(0)
}

@Serializable
class PopularDto(
    private val mangas: List<MangaDto>,
    private val pagination: PaginationDto,
) {
    fun toMangasPage() = mangas.toMangasPage(pagination.hasNext)
}

@Serializable
class ExploreDto(
    @SerialName("chapters") private val mangas: List<MangaDto>,
    private val pagination: PaginationDto,
) {
    fun toMangasPage() = mangas.toMangasPage(pagination.hasNext)
}

@Serializable
class PaginationDto(val hasNext: Boolean)

@Serializable
class HomeDto(
    private val chapters: List<LatestChapterDto>,
    private val pagination: HomePaginationDto,
) {
    fun toMangasPage() = chapters.map { it.manga }.toMangasPage(pagination.hasMoreChapters)
}

@Serializable
class LatestChapterDto(val manga: MangaDto)

@Serializable
class HomePaginationDto(val hasMoreChapters: Boolean)

private fun List<MangaDto>.toMangasPage(hasNextPage: Boolean) = MangasPage(
    filterNot { it.isNovel }.map { it.toSManga() }.distinctBy { it.url },
    hasNextPage,
)

@Serializable
class SeriesDto(
    val manga: MangaDto,
    private val lazy: LazyDto,
) {
    val chapters get() = lazy.chapters
}

@Serializable
class LazyDto(val chapters: List<ChapterDto>)

@Serializable
class MangaDto(
    private val title: String,
    // A number in lists, a string on the series page
    private val uploadId: JsonPrimitive,
    private val coverUrl: String? = null,
    private val categories: List<String> = emptyList(),
    private val altNames: List<String> = emptyList(),
    private val description: String? = null,
    private val writerIllustrator: String? = null,
    private val releaseYear: Int? = null,
    private val uploadstatus: String? = null,
) {
    // Light novels have text chapters
    val isNovel get() = "Light Novel" in categories

    fun toSManga() = SManga.create().apply {
        url = uploadId.content
        title = this@MangaDto.title
        thumbnail_url = coverUrl?.let { "$CDN_URL/$it" }
    }

    fun toSMangaDetails() = toSManga().apply {
        author = writerIllustrator?.ifBlank { null }
        genre = categories.joinToString().ifEmpty { null }
        status = when (uploadstatus) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(this@MangaDto.description)
            .field("Yayın yılı", releaseYear?.toString())
            .altTitles(altNames, "Alternatif isimler")
            .build()
    }
}

@Serializable
class ChapterDto(
    private val chapterNumber: JsonPrimitive,
    private val chapterTitle: String? = null,
    private val chapterSlug: String,
    private val createdAt: String? = null,
    private val versions: List<VersionDto>,
) {
    fun toSChapters(mangaId: String): List<SChapter> {
        val number = chapterNumber.content
        val path = "$mangaId/$number-$chapterSlug"
        return versions.mapIndexed { index, version ->
            SChapter.create().apply {
                // The reader opens the first version unless the URL names another one
                url = if (index == 0) path else "$path?version=${version.id}"
                name = if (chapterTitle.isNullOrBlank()) "Bölüm $number" else "Bölüm $number: $chapterTitle"
                chapter_number = chapterNumber.float
                scanlator = version.user?.username
                date_upload = Instant.tryParse(createdAt)
            }
        }
    }
}

@Serializable
class VersionDto(
    @SerialName("_id") val id: String,
    val user: UserDto? = null,
)

@Serializable
class UserDto(val username: String)

@Serializable
class ReaderDto(
    private val chapter: ReaderChapterDto,
    val requiresAuth: Boolean,
    val isAuthenticated: Boolean,
) {
    val versions get() = chapter.versions
}

@Serializable
class ReaderChapterDto(val versions: List<ReaderVersionDto>)

@Serializable
class ReaderVersionDto(
    @SerialName("_id") val id: String,
    val imageUrls: List<String>? = null,
)
