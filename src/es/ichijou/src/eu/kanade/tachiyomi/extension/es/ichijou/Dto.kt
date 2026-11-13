package eu.kanade.tachiyomi.extension.es.ichijou

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.Normalizer
import kotlin.time.Instant

@Serializable
class ApiResponse<T>(val data: T)

@Serializable
class Catalogue(val data: List<WorkCard>, val meta: Pagination)

@Serializable
class Pagination(val hasNext: Boolean)

@Serializable
class NamedValue(val name: String, val code: String? = null)

@Serializable
class WorkCard(
    val id: Int,
    val title: String,
    private val slug: String,
    val viewCount: Long,
    val type: NamedValue,
    private val synopsis: String?,
    private val publicationStatus: NamedValue?,
    private val workImages: List<CardImage>,
    private val workGenres: List<WorkGenre>,
    private val workContentWarnings: List<WorkWarning>,
) {
    fun hasGenre(name: String) = workGenres.any { it.genre.name == name }

    fun toSManga() = SManga.create().apply {
        url = "/obras/$id-$slug"
        title = this@WorkCard.title
        thumbnail_url = (
            workImages.firstOrNull { it.imageType.code == "card" && it.imageUrl.isNotBlank() }
                ?: workImages.firstOrNull { it.imageType.code == "cover" && it.imageUrl.isNotBlank() }
            )?.imageUrl?.withWorkId(id)
        description = synopsis
        genre = (listOf(type.name) + workGenres.map { it.genre.name } + workContentWarnings.map { it.contentWarning.name }).distinct().joinToString()
        status = publicationStatus.toStatus()
    }
}

@Serializable
class CardImage(
    @SerialName("image_type") val imageType: NamedValue,
    @SerialName("image_url") val imageUrl: String,
)

@Serializable
class WorkGenre(val genre: NamedValue)

@Serializable
class WorkWarning(val contentWarning: NamedValue)

@Serializable
class WorkDetails(
    private val id: Int,
    private val title: String,
    private val slug: String,
    private val synopsis: String?,
    private val publicationStatus: NamedValue?,
    val type: NamedValue,
    private val workImages: List<DetailImage>,
    private val workContentWarnings: List<ContentWarning>,
) {
    val coverUrl: String?
        get() = (
            workImages.firstOrNull { it.imageTypeCode == "card" && it.imageUrl.isNotBlank() }
                ?: workImages.firstOrNull { it.imageTypeCode == "cover" && it.imageUrl.isNotBlank() }
            )?.imageUrl

    fun toSManga(genres: List<NamedValue>, alternatives: List<AlternativeName>, groups: List<String>) = SManga.create().apply {
        url = "/obras/$id-$slug"
        title = this@WorkDetails.title
        thumbnail_url = coverUrl?.withWorkId(id)
        status = publicationStatus.toStatus()
        genre = (listOf(type.name) + genres.map { it.name } + workContentWarnings.map { it.name }).distinct().joinToString()
        description = MarkdownDescription.Builder()
            .info(type.name)
            .description(synopsis)
            .field("Traducción", groups)
            .altTitles(workContentWarnings.map { listOfNotNull("${it.name} (${it.minAge}+)", it.description).joinToString(": ") }, "Advertencias de contenido")
            .altTitles(alternatives.sortedBy { it.sortOrdex }.map { it.alternativeName }, "Nombres alternativos")
            .build()
    }
}

private fun NamedValue?.toStatus() = when (this?.code) {
    "finalized" -> SManga.COMPLETED
    "on-broadcast", "in-translation" -> SManga.ONGOING
    else -> SManga.UNKNOWN
}

@Serializable
class ContentWarning(val name: String, val minAge: Int, val description: String?)

@Serializable
class DetailImage(val imageTypeCode: String, val imageUrl: String)

@Serializable
class AlternativeName(val alternativeName: String, val sortOrdex: Int)

@Serializable
class Scan(val name: String)

@Serializable
class Joint(val scan: Scan)

@Serializable
class WorkContent(private val chapters: List<Chapter>?, private val volumes: List<Volume>?) {
    fun toChapters(mangaUrl: String, scanlator: String): List<SChapter> = (
        chapters.orEmpty().filter { it.needLectorImages }.sortedBy { it.orderIndex }.map { it.toSChapter(mangaUrl, scanlator) } +
            volumes.orEmpty().sortedBy { it.orderIndex }.flatMap { volume ->
                volume.chapters.filter { it.needLectorImages }.sortedBy { it.orderIndex }.map { it.toSChapter(mangaUrl, scanlator, volume.displayNumber) }
            }
        ).distinctBy { it.url }.reversed()
}

@Serializable
class Volume(val orderIndex: Int, val displayNumber: String?, val chapters: List<Chapter>)

@Serializable
class Chapter(
    private val id: Int,
    private val title: String,
    private val displayNumber: String,
    val orderIndex: Int,
    private val createdAt: String?,
    val needLectorImages: Boolean,
) {
    fun toSChapter(mangaUrl: String, group: String, volume: String? = null) = SChapter.create().apply {
        url = "$mangaUrl/capitulo/$id-${Normalizer.normalize(title.lowercase(), Normalizer.Form.NFD).replace(accents, "").replace(nonAlphanumeric, "-").trim('-')}"
        name = listOfNotNull(volume?.let { "Vol. $it" }, "Cap. $displayNumber", title).joinToString(" - ")
        scanlator = group.takeIf { it.isNotBlank() }
        date_upload = Instant.tryParse(createdAt)
        chapter_number = displayNumber.toFloatOrNull() ?: -1f
    }

    companion object {
        private val accents = Regex("\\p{M}+")
        private val nonAlphanumeric = Regex("[^a-z0-9]+")
    }
}

@Serializable
class Reader(val chapter: ReaderChapter)

@Serializable
class ReaderChapter(val images: List<PageImage>)

@Serializable
class PageImage(val pageIndex: Int, val imageUrl: String)

@Serializable
class Home(val newChaptersMangas: List<UpdatedChapter>)

@Serializable
class UpdatedChapter(val workId: Int)

private fun String.withWorkId(id: Int) = toHttpUrl().newBuilder().fragment(id.toString()).build().toString()
