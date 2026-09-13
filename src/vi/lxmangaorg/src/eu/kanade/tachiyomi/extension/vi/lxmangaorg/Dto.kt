package eu.kanade.tachiyomi.extension.vi.lxmangaorg

import kotlinx.serialization.Serializable

@Serializable
internal class FilterData(
    val classifications: List<FilterOption>,
    val genres: List<FilterOption>,
    val doujinshi: List<FilterOption>,
    val authors: List<FilterOption>,
)

@Serializable
internal class FilterOption(
    val name: String,
    val path: String,
) {
    override fun toString(): String = name
}

@Serializable
internal class ChapterListResponse(
    val data: ChapterListData,
)

@Serializable
internal class ChapterListData(
    val chapters: List<ChapterDto>,
)

@Serializable
internal class ChapterDto(
    val title: String,
    val link: String,
)
