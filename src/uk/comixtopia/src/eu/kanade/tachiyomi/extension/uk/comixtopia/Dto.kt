package eu.kanade.tachiyomi.extension.uk.comixtopia

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import kotlin.time.Instant

private const val IMG_HOST = "https://comicbookstorage.fra1.cdn.digitaloceanspaces.com"

// =========================== Search ============================
@Serializable
class TitlesList(
    private val slug: String,
    @SerialName("ukrainian_name") private val ukrainianName: String,
    private val cover: String,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = ukrainianName
        thumbnail_url = "$IMG_HOST/$cover"
    }
}

// =========================== Manga ============================
@Serializable
class MangaFull(
    private val slug: String,
    @SerialName("original_name") private val originalName: String? = null,
    @SerialName("ukrainian_name") private val ukrainianName: String,
    @SerialName("release_year") private val releaseYear: String? = null,
    @SerialName("comic_status") private val status: String,
    @SerialName("age_limit") private val ageLimit: Int? = null,
    private val description: String? = null,
    private val cover: String,
    private val authors: List<NameDto>? = null,
    private val publishers: List<NameDto>? = null,
    private val genres: List<NameDto>? = null,
    private val votes: List<RatingDto>? = null,
    private val issues: List<ChapterDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = ukrainianName
        thumbnail_url = "$IMG_HOST/$cover"
        description = buildString {
            originalName?.let { append("**Оригінальна назва**: $it\n") }
            votes?.map { it.rating }?.takeIf { it.isNotEmpty() }?.let { ratings ->
                append("**Рейтинг**: ${String.format(Locale.ROOT, "%.2f", ratings.average())}/5 (Голосів: ${ratings.count()})\n")
            }
            releaseYear?.let { append("**Рік випуску**: $it\n") }
            this@MangaFull.description?.let { append(it) }
        }
        author = authors?.joinToString { it.name }
        artist = publishers?.joinToString { it.name }
        genre = buildList {
            ageLimit?.let { add("$it+") }
            genres?.map { it.name }?.let { addAll(it) }
        }.joinToString()
        status = when (this@MangaFull.status) {
            "ongoing" -> SManga.ONGOING
            "finished" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    fun toSChapters(mangaUrl: String) = issues?.mapNotNull { chapter ->
        if (chapter.metadata?.state != "approved") return@mapNotNull null
        SChapter.create().apply {
            url = chapter.id.toString()
            name = "Розділ #${chapter.number}"
            chapter_number = chapter.number.toFloat()
            date_upload = Instant.tryParse(chapter.createdAt)
            scanlator = chapter.translator.takeIf { it.isNotBlank() }
            memo = buildJsonObject {
                put("pages", chapter.images.orEmpty().map { "$IMG_HOST/$it" }.toJsonElement())
                put("mangaId", mangaUrl)
            }
        }
    }.orEmpty()
}

@Serializable
class NameDto(
    val name: String,
)

@Serializable
class RatingDto(
    val rating: Int,
)

// =========================== Chapters ============================
@Serializable
class ChapterDto(
    val id: Int,
    @SerialName("issue_no") val number: Int,
    val translator: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("image_list") val images: List<String>? = null,
    val metadata: MetadataDto? = null,
)

@Serializable
class MetadataDto(
    val state: String? = null,
)

// =========================== Filters ============================
@Serializable
class FiltersDto(
    val genres: List<Pair<String, String>>? = emptyList(),
    val authors: List<Pair<String, String>>? = emptyList(),
    val publishers: List<Pair<String, String>>? = emptyList(),
)

@Serializable
class IdNameDto(
    val id: Int,
    val name: String,
)
