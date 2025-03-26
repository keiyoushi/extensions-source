package eu.kanade.tachiyomi.extension.pt.readmangas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
class WrapperResult<T>(
    val result: Result<T>? = null,
) {
    @Serializable
    class Result<T>(val `data`: Data<T>)

    @Serializable
    class Data<T>(val json: T)
}

interface Dto {
    val mangas: List<MangaDto>
    val nextCursor: String
    val hasNextPage: Boolean
}

@Serializable
class PopularResultDto(
    @SerialName("initialData")
    val result: MangaListDto,
    override val nextCursor: String = "",
) : Dto {
    override val mangas: List<MangaDto> get() = result.mangas
    override val hasNextPage: Boolean = false
}

@Serializable
class LatestResultDto(
    @SerialName("items")
    override val mangas: List<MangaDto>,
    override val nextCursor: String = "",
    override val hasNextPage: Boolean = false,
) : Dto

@Serializable
class MangaDetailsDto(
    @SerialName("oId")
    val slug: String,
    @SerialName("data")
    val details: MangaDto,
) {

    @Serializable
    class MangaDto(
        val id: String,
        @SerialName("title")
        val titles: List<Map<String, String>>,
        val description: String,
        @SerialName("coverImage")
        val thumbnailUrl: String,
        val status: String,
        val genres: List<Genre>,
    ) {
        val title: String get() = titles.first().values.first()
    }

    @Serializable
    class Genre(
        val name: String,
    )
}

@Serializable
class MangaListDto(
    @JsonNames("pages")
    val mangas: List<MangaDto>,
    @JsonNames("pageParams")
    val nextCursor: String = "",
)

@Serializable
class MangaDto(
    val author: String,
    @SerialName("coverImage")
    val thumbnailUrl: String,
    val id: String,
    val slug: String,
    val status: String,
    val title: String,
)

@Serializable
class ChapterListDto(
    val currentPage: Int,
    val chapters: List<ChapterDto>,
    val totalPages: Int,
) {
    fun hasNext() = currentPage < totalPages
}

@Serializable
class ChapterDto(
    val id: String,
    val title: String,
    val number: String,
    val createdAt: String,
)
