package eu.kanade.tachiyomi.extension.tr.hattorimanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class HMChapterDto(
    val chapters: List<ChapterDto>,
    val currentPage: Int,
    val lastPage: Int,
) {
    fun hasNextPage(): Boolean = currentPage < lastPage
}

@Serializable
class ChapterDto(
    @SerialName("title")
    val title: String,
    @SerialName("manga_slug")
    val slug: String,
    @SerialName("chapter_slug")
    val chapterSlug: String,
    @SerialName("formattedUploadTime")
    val date: String,
)

@Serializable
class HMLatestUpdateDto(
    val chapters: List<ChapterMangaDto>,
)

@Serializable
class ChapterMangaDto(
    val manga: LatestUpdateDto,
)

@Serializable
class LatestUpdateDto(
    val title: String,
    val slug: String,
    @SerialName("cover_image")
    val thumbnail: String,
)
