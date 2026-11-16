package eu.kanade.tachiyomi.extension.zh.kanman

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ResponseDto<T>(val data: T)

@Serializable
class ComicListDto(val data: List<ComicDto>)

@Serializable
class ComicDto(
    @SerialName("comic_id") private val comicId: Int,
    @SerialName("comic_name") private val comicName: String,
    @SerialName("cover_img") private val coverImg: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/$comicId/"
        title = comicName
        thumbnail_url = coverImg?.replaceFirst("http://", "https://")
    }
}

@Serializable
class ChapterInfoDto(@SerialName("current_chapter") val currentChapter: ChapterImagesDto)

@Serializable
class ChapterImagesDto(@SerialName("chapter_img_list") val chapterImgList: List<String>)
