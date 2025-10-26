package eu.kanade.tachiyomi.extension.ja.ganganonline

import kotlinx.serialization.Serializable

@Serializable
class NextData<T>(
    val props: Props<T>,
)

@Serializable
class Props<T>(
    val pageProps: PageProps<T>,
)

@Serializable
class PageProps<T>(
    val data: T,
)

@Serializable
class MangaListDto(
    val titleSections: List<MangaSectionDto>?, // Popular/Finished
    val sections: List<SearchSectionDto>?, // Search
    val ongoingTitleSection: MangaSectionDto?, // GA
    val finishedTitleSection: MangaSectionDto?, // GA
)

@Serializable
class MangaSectionDto(
    val titles: List<MangaDto>,
)

@Serializable
class SearchSectionDto(
    val titleLinks: List<MangaDto>,
)

@Serializable
class MangaDto(
    val titleId: Int,
    val header: String?, // Popular/Finished
    val name: String?, // Search
    val imageUrl: String?,
    val isNovel: Boolean?,
)

@Serializable
class PixivPageDto(
    val ganganTitles: List<PixivMangaDto>?,
)

@Serializable
class PixivMangaDto(
    val titleId: Int,
    val name: String,
    val imageUrl: String,
)

@Serializable
class MangaDetailDto(
    val default: MangaDetailDefaultDto,
)

@Serializable
class MangaDetailDefaultDto(
    val titleName: String,
    val author: String,
    val description: String,
    val imageUrl: String,
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    val id: Int,
    val status: Int?,
    val mainText: String,
    val subText: String?,
    val publishingPeriod: String?,
)

@Serializable
class PageListDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val image: PageImageUrlDto?,
    val linkImage: PageImageUrlDto?,
)

@Serializable
class PageImageUrlDto(
    val imageUrl: String,
)
