package eu.kanade.tachiyomi.extension.en.zinmangaart

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class MangaJsonLd(
    @SerialName("@type") val type: String? = null,
    val author: List<Author>? = null,
) {
    @Serializable
    class Author(val name: String? = null)
}

@Serializable
class RelatedManga(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = this@RelatedManga.url
        title = this@RelatedManga.title
        thumbnail_url = this@RelatedManga.thumbnailUrl
    }
}

fun SManga.toRelatedManga() = RelatedManga(url, title, thumbnail_url)

@Serializable
class ChapterListResponse(
    val data: ChapterData,
)

@Serializable
class ChapterData(
    val chapters: List<ChapterDto>,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
class ChapterDto(
    @SerialName("chapter_name") private val chapterName: String,
    @SerialName("chapter_slug") private val chapterSlug: String,
    @SerialName("chapter_num") private val chapterNum: Float? = null,
    @SerialName("updated_at") private val updatedAt: String? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = chapterSlug
        name = chapterName
        chapter_number = chapterNum ?: -1f
        date_upload = Instant.tryParse(updatedAt)
        memo = buildJsonObject { put("mangaSlug", mangaSlug) }
    }
}
