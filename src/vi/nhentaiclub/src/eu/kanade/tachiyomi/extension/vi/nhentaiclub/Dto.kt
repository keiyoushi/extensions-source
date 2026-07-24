package eu.kanade.tachiyomi.extension.vi.nhentaiclub

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.LocalDate
import java.time.ZoneId

@Serializable
internal class AdvancedSearchResponse(
    val data: List<MangaDto>,
    val total: Int,
)

@Serializable
internal class MangaDto(
    private val id: String,
    private val name: String,
    private val thumbnail: String? = null,
) {
    fun toSManga(imageUrl: String): SManga = SManga.create().apply {
        url = "/g/$id"
        title = name
        thumbnail_url = thumbnail?.takeIf(String::isNotBlank)
            ?: "$imageUrl/$id/thumbnail.jpg"
    }
}

@Serializable
internal class MangaDetailsDto(
    val id: String,
    private val name: String,
    private val thumbnail: String? = null,
    private val author: String? = null,
    private val status: String,
    private val genres: List<String>,
    private val introduction: String,
    private val chapterList: List<ChapterDto>,
) {
    fun toSManga(mangaUrl: String, imageUrl: String): SManga = SManga.create().apply {
        url = mangaUrl
        title = name
        thumbnail_url = thumbnail?.takeIf(String::isNotBlank)
            ?: "$imageUrl/$id/thumbnail.jpg"
        author = this@MangaDetailsDto.author
        description = introduction.trim().ifEmpty { null }
        genre = genres.joinToString().ifEmpty { null }
        status = when (this@MangaDetailsDto.status) {
            "progress" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    fun toSChapterList(baseUrl: String, language: String, zoneId: ZoneId): List<SChapter> = chapterList.map { chapter ->
        val chapterUrl = "$baseUrl/read/$id".toHttpUrl().newBuilder()
            .addPathSegment(chapter.name)
            .addQueryParameter("lang", language)
            .addQueryParameter("pages", chapter.pictures.toString())
            .build()

        SChapter.create().apply {
            url = chapterUrl.encodedPath + "?" + chapterUrl.encodedQuery
            name = if (chapter.name.equals("oneshot", ignoreCase = true)) {
                chapter.name
            } else {
                "Chapter ${chapter.name}"
            }
            chapter_number = chapter.name.toFloatOrNull() ?: -1F
            date_upload = runCatching {
                LocalDate.parse(chapter.createdAt)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
            }.getOrDefault(0L)
        }
    }

    fun pageCount(chapterName: String): Int? = chapterList
        .firstOrNull { it.name == chapterName }
        ?.pictures
}

@Serializable
internal class ChapterDto(
    val name: String,
    val pictures: Int,
    val createdAt: String,
)
