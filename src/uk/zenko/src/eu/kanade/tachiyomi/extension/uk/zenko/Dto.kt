package eu.kanade.tachiyomi.extension.uk.zenko

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

// ============================= Utilities ==============================
private fun getSelectedLanguage(isEng: String, engName: String?, name: String): String = when {
    isEng == "eng" && !engName.isNullOrEmpty() -> engName
    else -> name
}

private fun String.toStatus(): Int {
    val status = this.lowercase()
    return when (status) {
        "ongoing" -> SManga.ONGOING
        "finished" -> SManga.COMPLETED
        "paused" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }
}

private fun Long.secToMs(): Long = this * 1000

// ============================= DTO ==============================
@Serializable
class SearchResponse(
    val data: List<SearchDetailsResponse>,
    val meta: Meta,
)

@Serializable
class Meta(
    val hasNextPage: Boolean,
)

@Serializable
class SearchDetailsResponse(
    private val id: Int,
    private val name: String,
    private val engName: String? = null,
    private val coverImg: String,
    val category: String? = null,
) {
    fun toSManga(lang: String, imgUrl: String) = SManga.create().apply {
        url = "/titles/$id"
        title = getSelectedLanguage(lang, engName, name)
        thumbnail_url = "$imgUrl/$coverImg"
    }
}

@Serializable
class MangaDetailsResponse(
    private val id: Int,
    private val coverImg: String,
    private val description: String,
    private val translationStatus: String,
    private val name: String,
    private val engName: String? = null,
    private val originalName: String? = null,
    private val genres: List<NameList>? = null,
    private val tags: List<NameList>? = null,
    private val likesCount: Int? = null,
    private val viewsCount: Int? = null,
    private val bookmarksCount: Int? = null,
    private val writers: List<NameList>? = null,
    private val painters: List<NameList>? = null,
    private val ageLimit: Int? = null,
) {
    fun toSManga(lang: String, imgUrl: String) = SManga.create().apply {
        url = "/titles/$id"
        title = getSelectedLanguage(lang, engName, name)
        thumbnail_url = "$imgUrl/$coverImg"
        description = buildString {
            append(this@MangaDetailsResponse.description)
            append("\n")
            if (lang == "ua") {
                append("\nАльтернативні назви:")
                engName?.let { append(" $it,") }
                originalName?.let { append(" $it") }
            }
            likesCount?.let { append("\nВподобайок: $it ") }
            viewsCount?.let { append("\nПереглядів: $it ") }
            bookmarksCount?.let { append("\nВ закладинках у: $it ") }
        }
        genre = buildList {
            ageLimit?.takeIf { it != 0 }?.let { add("$it+") }
            genres?.map { it.name }?.let { addAll(it) }
            tags?.map { it.name }?.let { addAll(it) }
        }.joinToString()
        author = writers?.joinToString { it.name }
        artist = painters?.joinToString { it.name }
        status = this@MangaDetailsResponse.translationStatus.toStatus()
    }
}

@Serializable
class ChapterResponseItem(
    private val createdAt: Long? = null,
    val id: Int,
    val name: String?,
    val pages: List<Page>? = null,
    private val titleId: Int?,
    private val publisher: Publisher? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/titles/$titleId/$id"
        name = StringProcessor.format(this@ChapterResponseItem.name)
        date_upload = createdAt?.secToMs() ?: 0L
        scanlator = publisher?.name
        chapter_number = StringProcessor.number(this@ChapterResponseItem.name)
    }
}

@Serializable
class Page(
    val order: Int,
    val content: String,
)

@Serializable
class Publisher(
    val name: String? = null,
)

@Serializable
class NameList(
    val name: String,
)
