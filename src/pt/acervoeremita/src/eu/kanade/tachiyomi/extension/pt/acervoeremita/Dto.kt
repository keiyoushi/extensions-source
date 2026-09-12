package eu.kanade.tachiyomi.extension.pt.acervoeremita

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.string
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class MangaDto(
    private val workId: Long,
    private val title: String,
    private val coverUrl: String,
    private val tagNames: List<String>,
    private val slug: String,
) {
    fun toSManga() = SManga.create().apply {
        url = workId.toString()
        title = this@MangaDto.title
        thumbnail_url = coverUrl
        genre = tagNames.joinToString()
        memo = buildJsonObject {
            put("slug", slug)
        }
    }
}

@Serializable
class PageableMangas(
    val works: List<MangaDto>,
    val pagination: Page,
)

@Serializable
class Page(
    @JsonNames("hasMore")
    val hasNext: Boolean,
)

@Serializable
class WorkId(
    val workId: Long,
)

@Serializable
class PageableChapters(
    val chapters: List<ChapterDto>,
    val pagination: Page,
)

@Serializable
class ChapterDto(
    private val chapterId: Long,
    private val chapterNumber: String,
    private val name: String,
    private val releasedAt: String,
) {
    fun toSChapter(manga: SManga) = SChapter.create().apply {
        name = this@ChapterDto.name
        chapter_number = chapterNumber.toFloat()
        date_upload = Instant.tryParse(releasedAt)
        url = chapterId.toString()
        memo = buildJsonObject {
            put("slug", manga.memo["slug"]!!.string)
        }
    }
}
