package eu.kanade.tachiyomi.extension.ja.soraraw

import kotlinx.serialization.Serializable

@Serializable
data class NextDataDto(
    val buildId: String,
)

@Serializable
data class NextDataWrapperDto<T>(
    val pageProps: PagePropsDto<T>,
)

@Serializable
data class HtmlNextDataDto<T>(
    val props: NextDataWrapperDto<T>,
)

@Serializable
data class PagePropsDto<T>(
    val data: T,
)

@Serializable
data class MangaListDto(
    val list: List<MangaDto> = emptyList(),
)

@Serializable
data class TopMangasDto(
    val mangas: List<MangaDto> = emptyList(),
)

@Serializable
data class MangaDetailsDto(
    val manga: MangaDto,
)

@Serializable
data class ChapterDetailsDto(
    val chapter: ChapterDto,
)

@Serializable
data class MangaDto(
    val id: Int,
    val name: String? = null,
    val slug: String? = null,
    val img: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
    val summary: String? = null,
    val description: String? = null,
    val image: String? = null,
    val cover_url: String? = null,
    val cover: String? = null,
    val thumbnail: String? = null,
    val is_adult: String? = null,
    val mode: String? = null,
    val type: String? = null,
    val views: Int? = null,
    val number_bookmark: Int? = null,
    val updated_at: String? = null,
    val c_published_at: String? = null,
    val c_published: String? = null,
    val rate: kotlinx.serialization.json.JsonElement? = null,
    val number_rate: Int? = null,
    val content: String? = null,
    val names: List<NameDto>? = null,
    val alt_names: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val status: String? = null,
    val genres: List<kotlinx.serialization.json.JsonElement>? = null,
)

@Serializable
data class NameDto(
    val name: String,
)

@Serializable
data class GenreDto(
    val name: String,
    val slug: String,
)

@Serializable
data class ChapterDto(
    val id: Int,
    val name: kotlinx.serialization.json.JsonElement? = null,
    val title: String? = null,
    val order: kotlinx.serialization.json.JsonElement? = null,
    val manga_id: Int = -1,
    val uuid: String = "",
    val c_slug: String? = null,
    val path: String? = null,
    val updated_at: String? = null,
    val published_at: String? = null,
    val _b: String? = null,
    val _d: String? = null,
    val _p: String? = null,
    val _t: String? = null,
)

@Serializable
data class CryptedPagesDto(
    val d: String,
)

@Serializable
data class PageDataDto(
    val id: Int,
    val b: String? = null,
    val d: String? = null,
    val p: String? = null,
    val t: String? = null,
)
