package eu.kanade.tachiyomi.extension.en.doujinio

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class PageResponse<T>(val data: T)

@Serializable
class Manga(
    @SerialName("optimus_id")
    private val id: Int,
    private val title: String,
    private val description: String,
    private val thumb: String,
    private val tags: List<Tag>,
    @SerialName("creator_name")
    private val artist: String,
) {
    fun toSManga() = SManga.create().apply {
        url = "/manga/$id"
        title = this@Manga.title
        description = this@Manga.description
        thumbnail_url = this@Manga.thumb
        artist = this@Manga.artist
        genre = this@Manga.tags.joinToString(", ") { it.name }
        status = SManga.COMPLETED
        initialized = true
    }
}

@Serializable
class Tag(val id: Int, val name: String)

@Serializable
class Chapter(
    @SerialName("optimus_id")
    private val id: Int,
    @SerialName("manga_optimus_id")
    private val mangaId: Int,
    @SerialName("chapter_name")
    private val name: String,
    @SerialName("chapter_order")
    private val order: Int,
    @SerialName("published_at")
    private val publishedAt: String,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "manga/$mangaId/chapter/$id"
        // Can be equal to manga title (gets trimmed to empty)
        name = "\u2063" + this@Chapter.name
        date_upload = Instant.parseOrNull(publishedAt)?.toEpochMilliseconds() ?: 0L
    }
}

@Serializable
class ChapterMetadata(val identifier: String)

@Serializable
class ChapterPage(
    val href: String,
    val type: String,
)

@Serializable
class ChapterManifest(
    private val metadata: ChapterMetadata,
    @SerialName("readingOrder")
    private val pages: List<ChapterPage>,
) {
    fun toPageList(fragment: String) = pages
        .filter { page ->
            page.type.startsWith("image")
        }.mapIndexed { i, page ->
            Page(
                index = i,
                imageUrl = page.href + fragment,
            )
        }
}

@Serializable
class LatestRequest(
    val limit: Int,
    val offset: Int,
)

@Serializable
class SearchRequest(
    val keyword: String,
    val page: Int,
    val tags: List<Int> = emptyList(),
    val sort: String,
    val sort_dir: String,
)

@Serializable
class SearchResponse(
    val data: List<Manga>,
    val to: Int?,
    val total: Int,
)

@Serializable
class MangaKeys(
    private val chmkeys: List<Int>,
) {
    fun toByteArray(): ByteArray = ByteArray(chmkeys.size) { chmkeys[it].toByte() }
}
