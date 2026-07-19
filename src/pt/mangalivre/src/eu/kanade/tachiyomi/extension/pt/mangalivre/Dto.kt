package eu.kanade.tachiyomi.extension.pt.mangalivre

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

class WrapperDto(
    val mangas: List<MangaDto>,
    val pagination: PaginationDto,
) {
    val hasNextPage get() = pagination.hasNextPage

    companion object {
        fun fromJson(json: JSONObject) = WrapperDto(
            mangas = json.getJSONArray("mangas").mapObjects(MangaDto::fromJson),
            pagination = PaginationDto.fromJson(json.getJSONObject("pagination")),
        )
    }
}

class PaginationDto(
    val hasNextPage: Boolean,
) {
    companion object {
        fun fromJson(json: JSONObject) = PaginationDto(json.getBoolean("hasNextPage"))
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

        fun fromJson(json: JSONObject) = MangaDto(
            id = json.getString("id"),
            title = json.getString("title"),
            thumbnailUrl = json.optionalString("coverUrl"),
            authors = json.optJSONArray("authors")?.toStringList(),
            artists = json.optJSONArray("artists")?.toStringList(),
            genres = json.optJSONArray("genres")?.toStringList(),
            description = json.optionalString("description"),
            alternativeTitle = json.optionalString("alternativeTitle"),
            chapters = json.optJSONArray("chapters")?.mapObjects(ChapterDto::fromJson),
            status = json.optionalString("status"),
        )
    }
}

class ChapterReferenceDto(
    val mangaId: String,
    val chapterId: String,
) {
    fun toJson(): String = JSONObject()
        .put("mangaId", mangaId)
        .put("chapterId", chapterId)
        .toString()

    companion object {
        fun fromJson(value: String) = JSONObject(value).let {
            ChapterReferenceDto(it.getString("mangaId"), it.getString("chapterId"))
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
        fun fromJson(json: JSONObject) = ChapterDto(
            id = json.getString("id"),
            number = json.getString("number"),
            timestamp = json.getLong("timestamp"),
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
        fun fromJson(json: JSONObject) = PageDto(json.getJSONArray("pages").toStringList())
    }
}

private fun JSONObject.optionalString(key: String): String? = takeIf { has(key) && !isNull(key) }?.getString(key)?.takeIf { it.isNotBlank() }

private fun JSONArray.toStringList(): List<String> = List(length(), ::getString)

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> = List(length()) { transform(getJSONObject(it)) }
