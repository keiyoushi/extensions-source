package eu.kanade.tachiyomi.extension.zh.iqiyi

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ApiResponse<T>(
    val data: T,
)

@Serializable
class PopularityListDto(
    val popularityList: List<PopularComicDto> = emptyList(),
)

@Serializable
class PopularComicDto(
    private val comicId: Long,
    private val title: String,
    private val pic: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/detail_$comicId.html"
        title = this@PopularComicDto.title
        thumbnail_url = pic
    }
}

@Serializable
class SearchDto(
    val docinfos: List<DocInfoDto> = emptyList(),
)

@Serializable
class DocInfoDto(
    val albumDocInfo: AlbumDocInfoDto? = null,
)

@Serializable
class AlbumDocInfoDto(
    val comics: SearchComicDto? = null,
)

@Serializable
class SearchComicDto(
    private val id: Long,
    private val title: String,
    @SerialName("image_url") private val imageUrl: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/detail_$id.html"
        title = this@SearchComicDto.title
        thumbnail_url = imageUrl
    }
}

@Serializable
class ComicDetailDto(
    private val comicId: Long,
    private val title: String,
    private val pic: String? = null,
    private val authorsName: String? = null,
    private val comicTags: List<String> = emptyList(),
    private val brief: String? = null,
    private val serializeStatus: Int? = null,
    private val episodes: List<EpisodeDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/detail_$comicId.html"
        title = this@ComicDetailDto.title
        thumbnail_url = pic
        author = authorsName
        artist = authorsName
        genre = comicTags.filter { it.isNotBlank() }.joinToString()
        description = brief
        status = when (serializeStatus) {
            1 -> SManga.ONGOING
            2 -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }

    fun toChapterList() = episodes.map { it.toSChapter() }.reversed()
}

@Serializable
class EpisodeDto(
    private val comicId: Long,
    private val episodeId: Long,
    private val episodeTitle: String,
    private val episodeOrder: Int,
    private val firstOnlineTime: Long,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/reader/${comicId}_$episodeId.html"
        name = "$episodeOrder $episodeTitle"
        date_upload = firstOnlineTime
    }
}

@Serializable
class ReadDto(
    private val authPass: Int = 0,
    private val content: List<PageDto> = emptyList(),
) {
    fun toPageList(): List<Page> {
        if (authPass != 1 || content.isEmpty()) {
            throw Exception("本章为付费章节")
        }
        return content.mapIndexed { index, page -> Page(index, imageUrl = page.imageUrl) }
    }
}

@Serializable
class PageDto(
    val imageUrl: String,
)
