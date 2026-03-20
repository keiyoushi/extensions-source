package eu.kanade.tachiyomi.extension.fr.mangamoins

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private fun thumbnailUrl(baseUrl: String, folder: String) = "$baseUrl/files/scans/$folder/thumbnail.webp"

@Serializable
class MangaListResponse(
    val total: Int,
    val page: Int,
    val limit: Int,
    val data: List<MangaListItem>,
)

@Serializable
class MangaListItem(
    val title: String,
    @SerialName("cover_folder")
    val coverFolder: String,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        title = this@MangaListItem.title
        url = this@MangaListItem.title
        thumbnail_url = thumbnailUrl(baseUrl, coverFolder)
    }
}

@Serializable
class LatestChaptersResponse(
    val items: List<LatestChapterItem>,
)

@Serializable
class LatestChapterItem(
    val folder: String,
    val title: String,
) {
    fun toSManga(baseUrl: String, coverMap: Map<String, String>): SManga = SManga.create().apply {
        title = this@LatestChapterItem.title
        url = this@LatestChapterItem.title
        val coverFolder = coverMap[this@LatestChapterItem.title] ?: folder
        thumbnail_url = thumbnailUrl(baseUrl, coverFolder)
    }
}

@Serializable
class MangaDetailsResponse(
    val info: MangaInfo,
    val chapters: List<ChapterItem>,
)

@Serializable
class MangaInfo(
    val title: String,
    val author: String,
    val status: String,
    val cover: String,
    val description: String = "",
)

@Serializable
class ChapterItem(
    val folder: String,
    val num: String,
    val title: String,
    val time: Long,
)
