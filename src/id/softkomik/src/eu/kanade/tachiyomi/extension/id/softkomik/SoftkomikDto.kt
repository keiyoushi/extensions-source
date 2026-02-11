package eu.kanade.tachiyomi.extension.id.softkomik

import kotlinx.serialization.Serializable

@Serializable
data class LibDataDto(
    val page: Int,
    val maxPage: Int,
    val data: List<MangaDto>,
)

@Serializable
data class MangaDto(
    val title: String,
    val status: String? = null,
    val type: String? = null,
    val gambar: String,
    val title_slug: String,
)

@Serializable
data class MangaDetailsDto(
    val title: String,
    val title_alt: String? = null,
    val sinopsis: String? = null,
    val author: String? = null,
    val status: String? = null,
    val type: String? = null,
    val gambar: String,
    val updated_at: String? = null,
    val Genre: List<String>? = emptyList(),
)

@Serializable
data class ChapterDto(val chapter: String)

@Serializable
data class NextDataDto(val buildId: String)

@Serializable
data class ChapterPageImagesDto(val imageSrc: List<String>)

@Serializable
data class LibraryDto(val pageProps: LibraryPagePropsDto)

@Serializable
data class LibraryPagePropsDto(val libData: LibDataDto)

@Serializable
data class ListDto(val pageProps: ListPagePropsDto)

@Serializable
data class ListPagePropsDto(val data: LibDataDto)

@Serializable
data class DetailsDto(val pageProps: DetailsPagePropsDto)

@Serializable
data class DetailsPagePropsDto(val data: MangaDetailsDto)

@Serializable
data class ChapterListDto(val chapter: List<ChapterDto>)

@Serializable
data class ChapterPageDto(val props: ChapterPagePropsDto)

@Serializable
data class ChapterPagePropsDto(val pageProps: ChapterPageDataWrapperDto)

@Serializable
data class ChapterPageDataWrapperDto(val data: ChapterPageContentDto)

@Serializable
data class ChapterPageContentDto(val data: ChapterPageImagesDto)
