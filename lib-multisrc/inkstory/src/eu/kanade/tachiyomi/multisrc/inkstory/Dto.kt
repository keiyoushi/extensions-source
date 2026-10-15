package eu.kanade.tachiyomi.multisrc.inkstory

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseZonedDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.format.DateTimeFormatter

// ============================== Search ===============================
@Serializable
class MangaFromSearchDto(
    val book: BookFromSearchDto,
)

@Serializable
class BookFromSearchDto(
    private val slug: String,
    private val id: String,
    private val poster: String? = null,
    private val name: MangaNameDto,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        thumbnail_url = poster
        title = name.resolveTitle(slug)
        memo = buildJsonObject {
            put("id", id)
        }
    }
}

@Serializable
class MangaNameDto(
    private val ru: String? = null,
    private val en: String? = null,
    private val original: String? = null,
) {
    fun resolveTitle(fallback: String): String = ru?.takeIf(String::isNotBlank)
        ?: en?.takeIf(String::isNotBlank)
        ?: original?.takeIf(String::isNotBlank)
        ?: fallback
}

// ============================== Manga ===============================
@Serializable
class MangaFullDto(
    private val id: String,
    private val slug: String,
    private val name: MangaNameDto,
    private val poster: String? = null,
    private val description: String? = null,
    private val status: String? = null,
    private val labels: List<LabelDto>? = null,
    private val formats: List<String> = emptyList(),
    private val relations: List<RelationDto>? = null,
    private val externalLinks: List<String> = emptyList(),
    private val averageRating: Float? = null,
    private val ratingVotesCount: Int? = null,
    private val viewsCount: Int? = null,
    private val likesCount: Int? = null,
    private val bookmarksCount: Int? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        thumbnail_url = poster
        title = name.resolveTitle(slug)
        description = buildString {
            this@MangaFullDto.description?.trim()?.takeIf(String::isNotEmpty)?.let { append(it) }
            averageRating?.let {
                if (isNotEmpty()) append("\n\n")
                append("**Рейтинг**: %.2f".format(it))
                ratingVotesCount?.let { r -> append(" (оценок: $r)") }
            }
            viewsCount?.let {
                if (isNotEmpty()) append("\n")
                append("**Просмотры**: ${formatInt(it)}")
            }
            likesCount?.let {
                if (isNotEmpty()) append("\n")
                append("**Лайки**: ${formatInt(it)}")
            }
            bookmarksCount?.let {
                if (isNotEmpty()) append("\n")
                append("**Закладки**: ${formatInt(it)}")
            }
            if (externalLinks.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append("**Внешние ссылки**:\n")
                append(
                    externalLinks.joinToString("\n") {
                        "- [${it.substringAfter("://").substringBefore("/").removePrefix("www.")}]($it)"
                    },
                )
            }
        }
        author = relations
            ?.mapNotNull { rel ->
                rel.takeIf { it.type == "AUTHOR" }
                    ?.publisher?.name?.trim()?.takeIf(String::isNotEmpty)
            }
            ?.distinct()
            ?.joinToString()
            ?.ifBlank { null }
        artist = relations
            ?.mapNotNull { rel ->
                rel.takeIf { it.type == "ARTIST" }
                    ?.publisher?.name?.trim()?.takeIf(String::isNotEmpty)
            }
            ?.distinct()
            ?.joinToString()
            ?.ifBlank { null }
        status = when (this@MangaFullDto.status) {
            "ONGOING" -> SManga.ONGOING
            "DONE" -> SManga.COMPLETED
            "FROZEN" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        genre = buildList {
            labels?.mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }?.let { addAll(it) }
            addAll(formats.map { it.lowercase().replace("_", " ") })
        }.joinToString()
        memo = buildJsonObject {
            put("id", id)
        }
    }

    fun formatInt(value: Int): String = when {
        value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
        value >= 1_000 -> "%.1fk".format(value / 1_000.0)
        else -> value.toString()
    }

    @Serializable
    class LabelDto(
        val name: String? = null,
    )

    @Serializable
    class RelationDto(
        val type: String? = null,
        val publisher: PublisherDto? = null,
    )
}

@Serializable
class PublisherDto(
    val name: String? = null,
)

// ============================== Chapters ===============================
@Serializable
class ChapterDto(
    private val id: String,
    private val name: String? = null,
    private val title: String? = null,
    private val number: Double? = null,
    private val volume: Double? = null,
    private val branchId: String? = null,
    private val createdAt: String? = null,
) {
    fun toSChapter(branches: Map<String, String?>, slug: String): SChapter = SChapter.create().apply {
        url = id
        val vol = volume?.toString()?.removeSuffix(".0")?.takeIf(String::isNotBlank)
        val num = number?.toString()?.removeSuffix(".0")?.takeIf(String::isNotBlank)
        val baseChapterName = when {
            vol != null && num != null -> "Том $vol Глава $num"
            num != null -> "Глава $num"
            vol != null -> "Том $vol"
            else -> "Глава"
        }
        val subtitle = listOf(this@ChapterDto.name, title)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
        name = if (!subtitle.isNullOrBlank() && !baseChapterName.equals(subtitle, true)) {
            "$baseChapterName - $subtitle"
        } else {
            baseChapterName
        }
        chapter_number = number?.toFloat() ?: -1f
        date_upload = zonedDateTimeFormat.tryParseZonedDateTime(createdAt)
        branchId?.let { scanlator = branches[it]?.takeIf(String::isNotBlank) }
        memo = buildJsonObject {
            put("slug", slug)
        }
    }
    companion object {
        private val zonedDateTimeFormat = DateTimeFormatter.ofPattern("[yyyy-MM-dd'T'HH:mm:ss.SSSX][yyyy-MM-dd'T'HH:mm:ssX][yyyy-MM-dd'T'HH:mm:ss.SSSSSSX]")
    }
}

@Serializable
class BranchDto(
    val id: String,
    private val publishers: List<PublisherDto> = emptyList(),
) {
    fun publisherName(): String? = publishers
        .mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }
        .distinct()
        .joinToString()
        .ifBlank { null }
}

// ============================== Pages ===============================
@Serializable
class PagesDto(
    val pages: List<ChapterPageDto>,
)

@Serializable
class ChapterPageDto(
    val index: Int? = null,
    val image: String? = null,
)

enum class ImageCodec {
    SEC,
    XOR,
}

// ============================== Filters ===============================
@Serializable
class FiltersData(
    val genres: List<Pair<String, String>>? = emptyList(),
)

@Serializable
class GenresDto(
    val kind: String,
    val name: String,
    val slug: String,
)
