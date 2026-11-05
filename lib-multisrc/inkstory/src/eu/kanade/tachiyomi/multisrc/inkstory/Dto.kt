package eu.kanade.tachiyomi.multisrc.inkstory

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale
import kotlin.time.Instant

@Serializable
class CatalogueInput(
    private val size: Int,
    private val page: Int,
    private val sort: String? = null,
    private val orderBy: String? = null,
    private val readingRestriction: List<String>? = null,
    private val search: String? = null,
    private val relatedBookId: String? = null,
    private val labelsInclude: List<String>? = null,
    private val labelsExclude: List<String>? = null,
    private val strictLabelEqual: Boolean? = null,
    private val status: List<String>? = null,
    private val country: List<String>? = null,
    private val contentStatus: List<String>? = null,
    private val formats: List<String>? = null,
    private val averageRatingMin: Int? = null,
    private val averageRatingMax: Int? = null,
    private val yearMin: Int? = null,
    private val yearMax: Int? = null,
    private val chaptersCountMin: Int? = null,
    private val chaptersCountMax: Int? = null,
)

@Serializable
class BookInput(
    private val slug: String,
    private val createView: Boolean,
)

@Serializable
class ChapterInput(
    private val id: String,
    private val createView: Boolean,
)

@Serializable
class CatalogueDto(
    val total: Int,
    val books: List<BookDto>,
)

@Serializable
class BookDto(
    val id: String,
    private val slug: String,
    private val poster: String? = null,
    private val name: NameDto,
    private val altNames: List<AltNameDto> = emptyList(),
    private val description: String? = null,
    private val status: String? = null,
    private val country: String? = null,
    private val year: Int? = null,
    private val formats: List<String> = emptyList(),
    private val averageRating: Double? = null,
    private val ratingVotesCount: Int? = null,
    private val viewsCount: Int,
    private val likesCount: Int,
    private val labels: List<LabelDto> = emptyList(),
    private val relations: List<RelationDto> = emptyList(),
    private val externalLinks: List<String> = emptyList(),
) {
    fun toSManga(titleLanguage: String) = SManga.create().apply {
        url = slug
        title = name.title(titleLanguage)
        thumbnail_url = poster
        memo = buildJsonObject { put("id", id) }
    }

    fun toSMangaDetails(titleLanguage: String) = toSManga(titleLanguage).apply {
        author = relations.publishers("AUTHOR")
        artist = relations.publishers("ARTIST")
        status = when (this@BookDto.status) {
            "ONGOING" -> SManga.ONGOING
            "DONE" -> SManga.COMPLETED
            "FROZEN" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        genre = buildList {
            formats.mapNotNullTo(this) { FORMATS[it] }
            labels.filter { it.kind == "GENRE" }.mapTo(this) { it.name }
            labels.filter { it.kind == "TAG" }.mapTo(this) { it.name }
        }.distinct().joinToString()
        description = MarkdownDescription.Builder()
            .rating(averageRating?.takeIf { ratingVotesCount != 0 }, votes = ratingVotesCount)
            .info(year?.toString(), COUNTRIES[country], "Просмотры ${viewsCount.compact()}", "Лайки ${likesCount.compact()}")
            .description(this@BookDto.description)
            .field("Журнал", relations.publishers("MAGAZINE"))
            .field("Издатель", relations.publishers("PUBLISHER"))
            .altTitles((name.all + altNames.map { it.name }).filterNot { it == title }, "Другие названия")
            .links(externalLinks.mapNotNull { link -> link.toHttpUrlOrNull()?.let { it.trackerName() to link } }, "Внешние трекеры")
            .build()
    }
}

private fun List<RelationDto>.publishers(type: String) = filter { it.type == type }
    .map { it.publisher.name.trim() }
    .asReversed()
    .distinct()
    .joinToString()
    .ifEmpty { null }

private fun HttpUrl.trackerName(): String = host.removePrefix("www.").let { TRACKERS[it] ?: it }

@Serializable
class NameDto(
    private val ru: String? = null,
    private val en: String? = null,
    private val original: String? = null,
) {
    val all get() = listOfNotNull(ru, en, original)

    fun title(language: String) = when (language) {
        "en" -> en
        "original" -> original
        else -> ru
    } ?: all.first()
}

// Counts as the site shows them: 4.06M, 182k, 1.09k
private fun Int.compact(): String {
    val (value, suffix) = when {
        this >= 999_500 -> this / 1_000_000.0 to "M"
        this >= 1_000 -> this / 1_000.0 to "k"
        else -> return toString()
    }
    val decimals = when {
        value < 10 -> 2
        value < 100 -> 1
        else -> 0
    }
    return "%.${decimals}f".format(Locale.ROOT, value).trimEnd('0').trimEnd('.') + suffix
}

@Serializable
class AltNameDto(
    val name: String,
)

@Serializable
class LabelDto(
    val kind: String,
    val name: String,
)

@Serializable
class RelationDto(
    val type: String,
    val publisher: PublisherDto,
)

@Serializable
class PublisherDto(
    val name: String,
)

@Serializable
class ChapterListDto(
    val branches: List<BranchDto>,
    val chapters: List<ChapterDto>,
)

@Serializable
class BranchDto(
    val id: String,
)

@Serializable
class ChapterDto(
    private val id: String,
    private val title: String,
    private val number: Double,
    private val volume: Double? = null,
    private val createdAt: String,
    val branchId: String,
    val publisherNames: String? = null,
) {
    val key get() = volume to number

    fun toSChapter(slug: String) = SChapter.create().apply {
        url = id
        name = listOfNotNull(volume?.let { "Том ${it.toString().removeSuffix(".0")}" }, title).joinToString(" ")
        chapter_number = number.toFloat()
        date_upload = Instant.tryParse(createdAt)
        scanlator = publisherNames
        memo = buildJsonObject { put("slug", slug) }
    }
}

@Serializable
class ChapterPagesDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val index: Int,
    val image: String,
)

@Serializable
class LabelListItemDto(
    val id: String,
    val kind: String,
    val name: String,
)

@Serializable
class FilterData(
    val genres: List<Pair<String, String>>,
    val tags: List<Pair<String, String>>,
)

internal val FORMATS = mapOf(
    "FOURTH_KOMA" to "Ёнкома",
    "DOUJINSHI" to "Додзинси",
    "COMPILATION" to "Сборник",
    "COLORED" to "Цветной",
    "SINGLE" to "Сингл",
    "WEB" to "Веб",
    "WEBTOON" to "Вебтун",
    "ARTBOOK" to "Артбук",
    "LIGHT" to "Печать",
)

internal val COUNTRIES = mapOf(
    "JAPAN" to "Япония",
    "CHINA" to "Китай",
    "KOREA" to "Корея",
    "RUSSIA" to "Россия",
    "OTHER" to "Другая",
)

private val TRACKERS = mapOf(
    "mangaupdates.com" to "MangaUpdates",
    "mangabaka.org" to "MangaBaka",
    "shikimori.one" to "Shikimori",
    "shikimori.me" to "Shikimori",
    "myanimelist.net" to "MyAnimeList",
    "anilist.co" to "AniList",
)
