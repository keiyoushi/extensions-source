package eu.kanade.tachiyomi.extension.all.kagane

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import java.util.Locale
import kotlin.time.Instant

@Serializable
class GenreDto(
    val id: String,
    @SerialName("genre_name")
    val genreName: String,
)

@Serializable
class TagDto(
    val id: String,
    @SerialName("tag_name")
    val tagName: String,
)

@Serializable
class SourcesDto(
    val sources: List<SourceDto>,
)

@Serializable
class SourceDto(
    @SerialName("source_id") val sourceId: String,
    @SerialName("source_type") val sourceType: String, // "Official", "Unofficial", "Mixed"
    val title: String,
)

@Serializable
class SearchDto(
    val content: List<Book> = emptyList(),
    val last: Boolean = true,
    @SerialName("total_elements")
    val totalElements: Int = 0,
    @SerialName("total_pages")
    val totalPages: Int = 0,
) {
    fun hasNextPage() = !last

    @Serializable
    class Book(
        @SerialName("series_id")
        val id: String,
        val title: String,
        @SerialName("source_id")
        val sourceId: String? = null,
        @SerialName("current_books")
        val booksCount: Int,
        @SerialName("start_year")
        val startYear: Int? = null,
        @SerialName("cover_image_id")
        val coverImage: String? = null,
        @SerialName("alternate_titles")
        val alternateTitles: List<String> = emptyList(),
    ) {

        fun toSManga(apiUrl: String, showSource: Boolean, sources: Map<String, String>, cleanTitle: Boolean): SManga = SManga.create().apply {
            title = if (showSource && !sources.isEmpty()) "${this@Book.title.trim()} [${sources[this@Book.sourceId]}]" else this@Book.title.clean(cleanTitle)
            url = id
            thumbnail_url = coverImage?.let { "$apiUrl/image/$it" }
        }
    }
}

@Serializable
class TrackerDto(
    @SerialName("book_series")
    val bookSeries: List<Book> = emptyList(),
) {
    @Serializable
    class Book(
        val id: String,
        val title: String,
        @SerialName("source_id")
        val sourceId: String? = null,
        @SerialName("current_books")
        val booksCount: Int,
        @SerialName("cover_image_id")
        val coverImage: String? = null,
    ) {

        fun toSManga(apiUrl: String, showSource: Boolean, sources: Map<String, String>, cleanTitle: Boolean): SManga = SManga.create().apply {
            title = if (showSource && !sources.isEmpty()) "${this@Book.title.trim()} [${sources[this@Book.sourceId]}]" else this@Book.title.clean(cleanTitle)
            url = id
            thumbnail_url = coverImage?.let { "$apiUrl/image/$it" }
        }
    }
}

@Serializable
class StaffSearchDto(
    val content: List<Staff> = emptyList(),
) {
    @Serializable
    class Staff(
        @SerialName("staff_id")
        val staffId: String,
        val name: String,
        @SerialName("native_name")
        val nativeName: String? = null,
        @SerialName("alternate_names")
        val alternateNames: List<String> = emptyList(),
    )
}

@Serializable
class GroupSearchDto(
    val items: List<Group> = emptyList(),
) {
    @Serializable
    class Group(
        @SerialName("group_id")
        val groupId: String,
        val title: String,
    )
}

@Serializable
class DetailsDto(
    val title: String,
    val slug: String? = null,
    val description: String?,
    @SerialName("upload_status")
    val publicationStatus: String,
    val format: String?,
    @SerialName("source_id")
    val sourceId: String?,
    @SerialName("series_staff")
    val seriesStaff: List<SeriesStaff> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val tags: List<Tag> = emptyList(),
    @SerialName("series_alternate_titles")
    val seriesAlternateTitles: List<AlternateTitle> = emptyList(),
    @SerialName("edition_info")
    val editionInfo: String? = null,
    @SerialName("tracker_id")
    val trackerId: String? = null,
    @SerialName("series_links")
    val seriesLinks: List<SeriesLink> = emptyList(),
    @SerialName("average_rating")
    val averageRating: Double? = null,
    @SerialName("total_ratings")
    val totalRatings: Int? = null,
    @SerialName("series_covers")
    val covers: List<SeriesCover> = emptyList(),
    @SerialName("age_rating")
    val ageRating: String? = null,
    @SerialName("is_pornographic")
    val isPornographic: Boolean? = null,
    @SerialName("is_disturbing")
    val isDisturbing: Boolean? = null,
    @SerialName("original_language")
    val originalLanguage: String? = null,
    @SerialName("translated_language")
    val translatedLanguage: String? = null,
    @SerialName("start_year")
    val startYear: Int? = null,
    @SerialName("end_year")
    val endYear: Int? = null,
    @SerialName("current_volumes")
    val currentVolumes: Int? = null,
    @SerialName("total_views")
    val totalViews: Long? = null,
    @SerialName("subscriber_count")
    val subscriberCount: Int? = null,
) {
    @Serializable
    class SeriesStaff(
        @SerialName("staff_id")
        val staffId: String,
        val name: String,
        @SerialName("native_name")
        val nativeName: String? = null,
        val role: String,
        val priority: Int = 0,
    )

    @Serializable
    class Genre(
        @SerialName("genre_name")
        val genreName: String,
    )

    @Serializable
    class Tag(
        @SerialName("tag_name")
        val tagName: String,
    )

    @Serializable
    class AlternateTitle(
        val title: String,
        val label: String?,
    )

    @Serializable
    class SeriesCover(
        @SerialName("image_id")
        val imageId: String,
    )

    @Serializable
    class SeriesLink(
        val url: String,
        val label: String? = null,
        val kind: String? = null,
    )

    fun toSManga(apiUrl: String, sourceName: String? = null, baseUrl: String = "", showEdition: Boolean = false, showSource: Boolean = false, showDetails: Boolean = false, cleanTitle: Boolean): SManga = SManga.create().apply {
        val base = this@DetailsDto.title.trim()
        val withEdition = if (showEdition && !this@DetailsDto.editionInfo.isNullOrBlank()) "$base (${this@DetailsDto.editionInfo})" else base
        title = if (showSource && sourceName != null) "$withEdition [$sourceName]" else withEdition.clean(cleanTitle)
        thumbnail_url = covers.firstOrNull()?.imageId?.let { "$apiUrl/image/$it" }

        val byAuthorRoles = seriesStaff.filter { it.roleIs(BOOK_CREATOR_ROLES) }
        val byArtistRoles = seriesStaff.filter { it.roleIs(ARTIST_ROLES) }

        description = buildString {
            this@DetailsDto.description?.takeIf { it.isNotBlank() }?.let {
                append(Jsoup.parse(it.trim().replace("\n", "<br>")).wholeText())
                append("\n")
            }

            if (showDetails) {
                buildList {
                    averageRating?.let { rating ->
                        add(
                            "**Rating**: ${"%.1f".format(Locale.US, rating / 10)}/10" +
                                (totalRatings?.let { count -> " ($count ratings)" } ?: ""),
                        )
                    }
                    format?.takeIf { it.isNotBlank() }?.let { add("**Format**: $it") }
                    ageRating?.let { add("**Content Rating**: ${it.toContentRating(isPornographic, isDisturbing)}") }
                    val originalName = originalLanguage?.let { LANGUAGE_NAMES[it] }
                    originalName?.let { add("**Original Language**: $it") }
                    translatedLanguage?.let { LANGUAGE_NAMES[it] }?.takeIf { it != originalName }?.let { add("**Translated Language**: $it") }
                    currentVolumes?.takeIf { it > 0 }?.let { add("**Volumes**: $it") }
                    startYear?.takeIf { it > 0 }?.let { start ->
                        endYear?.takeIf { end -> end > start }
                            ?.let { end -> add("**Year**: $start–$end") }
                            ?: add("**Year**: $start")
                    }
                    totalViews?.takeIf { it > 0 }?.let { add("**Views**: ${it.toCompactCount()}") }
                    subscriberCount?.takeIf { it > 0 }?.let { add("**Followers**: ${it.toLong().toCompactCount()}") }
                }
                    .sortedBy { it }
                    .forEach { append("$it\n") }
            }

            if (sourceName != null && sourceId != null) {
                if (isNotEmpty()) append("\n")
                append("**Source**: [$sourceName]($baseUrl/sources/$sourceId)\n")
            }

            seriesStaff
                .filter { !it.roleIs(BOOK_CREATOR_ROLES) && !it.roleIs(ARTIST_ROLES) }
                .sortedBy { it.priority }
                .groupBy { it.role }
                .forEach { (role, members) ->
                    append("**$role**: ${members.joinToString { it.name.displayWithNative(it.nativeName) }}\n")
                }

            if (showDetails) {
                val official = seriesLinks.filter { it.kind == "Official" }
                val trackers = seriesLinks.filter { it.kind == "Tracker" }
                val others = seriesLinks.filter { it.kind != null && it.kind != "Official" && it.kind != "Tracker" }
                val linkSections = buildString {
                    listOf(
                        "**Official Link(s)**:" to official,
                        "**Tracker(s)**:" to trackers,
                        "**Other(s)**:" to others,
                    )
                        .filter { it.second.isNotEmpty() }
                        .forEachIndexed { index, (header, links) ->
                            if (index > 0) append("\n")
                            append("$header\n")
                            links.forEach { link ->
                                append("- [${link.label ?: link.url}](${link.url})\n")
                            }
                        }
                }
                if (linkSections.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append(linkSections)
                }
            }

            if (seriesAlternateTitles.isNotEmpty()) {
                append("\n**Associated Name(s):**\n")
                seriesAlternateTitles.forEach { alt ->
                    append("- ${alt.title}\n")
                }
            }
        }.trim()

        author = byAuthorRoles.sortedBy { it.priority }.distinctBy { it.staffId }.joinToString { it.name }
        artist = byArtistRoles.sortedBy { it.priority }.distinctBy { it.staffId }.joinToString { it.name }
        genre = genres.map { it.genreName }.joinToString()
        status = this@DetailsDto.publicationStatus.toStatus()
    }

    private fun String.toStatus(): Int = when (this.uppercase()) {
        "ONGOING" -> SManga.ONGOING
        "COMPLETED" -> SManga.COMPLETED
        "HIATUS" -> SManga.ON_HIATUS
        "ABANDONED" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

private val LANGUAGE_NAMES = mapOf(
    "en" to "English",
    "ja" to "Japanese",
    "ko" to "Korean",
    "zh" to "Chinese",
    "es" to "Spanish",
    "es-419" to "Latin American Spanish",
    "fr" to "French",
    "de" to "German",
    "pt" to "Portuguese",
    "pt-BR" to "Brazilian Portuguese",
    "ru" to "Russian",
    "it" to "Italian",
    "id" to "Indonesian",
    "vi" to "Vietnamese",
    "th" to "Thai",
    "pl" to "Polish",
    "hi" to "Hindi",
    "ar" to "Arabic",
)

private val BOOK_CREATOR_ROLES = setOf("author", "story", "original creator")
private val ARTIST_ROLES = setOf("artist", "art")

private val CAMEL_CASE_WEIGHT = Regex("(?<!^)([A-Z])")

private fun Long.toCompactCount(): String = when {
    this >= 1_000_000_000 -> "%.1f".format(Locale.US, this / 1_000_000_000.0).removeSuffix(".0") + "B"
    this >= 1_000_000 -> "%.1f".format(Locale.US, this / 1_000_000.0).removeSuffix(".0") + "M"
    this >= 1_000 -> "%.1f".format(Locale.US, this / 1_000.0).removeSuffix(".0") + "K"
    else -> toString()
}

private fun DetailsDto.SeriesStaff.roleIs(roles: Set<String>): Boolean = role.trim().lowercase() in roles

private fun String.displayWithNative(native: String?): String = when {
    native.isNullOrBlank() || native == this -> this
    else -> "$this ($native)"
}

private fun String.toContentRating(isPornographic: Boolean?, isDisturbing: Boolean?): String {
    val base = when (this) {
        "Safe" -> "Safe"
        "FifteenPlus" -> "Fifteen Plus"
        "EighteenPlus" -> "Eighteen Plus"
        else -> replace(CAMEL_CASE_WEIGHT, " $1").trim()
    }
    val flags = buildList {
        if (isPornographic == true) add("Pornographic")
        if (isDisturbing == true) add("Disturbing")
    }
    return if (flags.isEmpty()) base else "$base (${flags.joinToString(", ")})"
}

@Serializable
class BookIndexDto(
    private val count: Int,
    private val fields: List<String>,
    private val groups: List<ChapterDto.Group>,
    private val books: List<List<JsonElement>>,
) {
    fun toBooks(): List<ChapterDto.Book> {
        check(books.size == count) { "Incomplete Kagane chapter index" }
        // The compact index declares its column order in fields.
        return books.map { row ->
            val values = fields.zip(row).toMap()
            ChapterDto.Book(
                id = values.getValue("book_id").parseAs<String>(),
                title = values.getValue("title").parseAs<String>(),
                createdAt = values.getValue("released_at").parseAs<Long?>()
                    ?.let { Instant.fromEpochSeconds(it).toString() },
                pagesCount = values.getValue("page_count").parseAs<Int>(),
                number = values.getValue("sort_no").parseAs<Float>(),
                chapterNo = values.getValue("chapter_no").parseAs<String?>(),
                volumeNo = values.getValue("volume_no").parseAs<String?>(),
                groups = values.getValue("group_ix").parseAs<List<Int>>().map { groups[it] },
            )
        }
    }
}

@Serializable
class ChapterDto(
    @SerialName("series_books")
    val seriesBooks: List<Book>,
) {
    @Serializable
    class Book(
        @SerialName("book_id")
        val id: String,
        @SerialName("series_id")
        val seriesId: String? = null,
        val title: String,
        @SerialName("created_at")
        val createdAt: String?,
        @SerialName("page_count")
        val pagesCount: Int,
        @SerialName("sort_no")
        val number: Float,
        @SerialName("chapter_no")
        val chapterNo: String?,
        @SerialName("volume_no")
        val volumeNo: String?,
        val groups: List<Group> = emptyList(),
    ) {
        fun toSChapter(seriesId: String, slug: String?, useSourceChapterNumber: Boolean = false, chapterTitleMode: String = "optional"): SChapter = SChapter.create().apply {
            url = "/series/$seriesId/reader/$id"
            memo = buildJsonObject {
                slug?.takeIf { it.isNotBlank() }?.let { put("seriesSlug", it) }
            }
            name = buildChapterName(chapterTitleMode)
            date_upload = Instant.tryParse(createdAt)
            if (useSourceChapterNumber) {
                chapter_number = number
            }
            scanlator = buildString {
                append(groups.joinToString(", ") { it.title })

                // Extract group tags in chapter title
                CHAPTER_GROUP_REGEX.matchEntire(METADATA_REGEX.replace(title.trim(), ""))?.let { match ->
                    val groupTag = match.groups[1]?.value ?: match.groups[2]?.value
                    groupTag?.let { append(" ($it)") }
                }
            }
        }

        private fun buildChapterName(mode: String = "optional"): String {
            val trimmedTitle = title.trim()
            return when (mode) {
                "optional" -> {
                    when {
                        trimmedTitle.isEmpty() && chapterNo.isNullOrBlank() && !volumeNo.isNullOrBlank() -> buildChapterName("vol_chapter")
                        trimmedTitle.isEmpty() && !chapterNo.isNullOrBlank() -> "Ch.$chapterNo"
                        else -> trimmedTitle
                    }
                }

                "always" -> {
                    when {
                        chapterNo.isNullOrBlank() && !volumeNo.isNullOrBlank() -> buildChapterName("vol_chapter")
                        trimmedTitle.isEmpty() -> "Ch.$chapterNo"
                        else -> "Ch.$chapterNo $trimmedTitle"
                    }
                }

                "vol_chapter", "vol_local" -> {
                    val volPart = if (!volumeNo.isNullOrBlank()) "Vol.$volumeNo " else ""
                    val chPart = if (!chapterNo.isNullOrBlank()) "Ch.$chapterNo" else ""
                    val numPart = "$volPart$chPart".trim()
                    when {
                        numPart.isEmpty() -> trimmedTitle
                        trimmedTitle.isEmpty() || mode == "vol_local" -> numPart
                        else -> "$numPart $trimmedTitle"
                    }
                }

                else -> trimmedTitle
            }
        }
    }

    @Serializable
    class Group(
        val title: String,
    )
}

@Serializable
class ChallengeDto(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("cache_url")
    val cacheUrl: String,
    val manifest: ManifestDto? = null,
)

@Serializable
class ManifestDto(
    val pages: List<PageDto> = emptyList(),
)

@Serializable
class PageDto(
    @SerialName("page_no")
    val pageNumber: Int,
    @SerialName("page_id")
    val pageUuid: String,
    val ext: String? = null,
)

@Serializable
class IntegrityDto(
    val token: String,
    val exp: Long,
)

private val BRACKET_REGEX = Regex("""(\([^()]*\)|\[[^\[\]]*\])\s*$""")

private val METADATA_REGEX = Regex("""(?:\s*\{[^{}]*\})+\s*$""")

private val CHAPTER_GROUP_REGEX = Regex(
    """^Chapter\s+.*(?:-\s*Volume\s+.*\(([^()]+)\)|\[([^\[\]]+)\])\s*$""",
    RegexOption.IGNORE_CASE,
)

private fun String.clean(removeExtras: Boolean): String = if (removeExtras) this.replace(BRACKET_REGEX, "").trim() else this.trim()
