package eu.kanade.tachiyomi.extension.en.mlbblore

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// type=3 from API payload; represents comic albums
internal const val TYPE_COMIC = 3

@Serializable
class ApiListResponse(
    val data: List<AlbumEntry> = emptyList(),
)

@Serializable
class ApiDetailResponse(
    val data: AlbumDetail? = null,
)

@Serializable
class AlbumEntry(
    val id: Int = 0,
    val type: Int = 0,
    val title: String = "",
    @SerialName("hero_name") val heroName: String = "",
    val thumb: String = "",
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = this@AlbumEntry.title
        author = heroName.trim()
        thumbnail_url = thumb.toAbsoluteUrl()
    }

    fun isComic() = type == TYPE_COMIC
}

@Serializable
class AlbumDetail(
    val id: Int = 0,
    val title: String = "",
    @SerialName("hero_name") val heroName: String = "",
    val thumb: String = "",
    @SerialName("share_content") val shareContent: String = "",
    @SerialName("comic_content") val comicContent: List<String> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = this@AlbumDetail.title
        author = heroName.trim()
        thumbnail_url = thumb.toAbsoluteUrl()
        description = shareContent
        status = SManga.COMPLETED
        initialized = true
    }

    fun toSChapter() = SChapter.create().apply {
        name = "Chapter 1"
        chapter_number = 1f
        url = id.toString()
    }

    fun toPageList() = comicContent.mapIndexed { index, raw ->
        Page(index, imageUrl = raw.toAbsoluteUrl())
    }
}

private fun String.toAbsoluteUrl() = if (startsWith("//")) "https:$this" else this
