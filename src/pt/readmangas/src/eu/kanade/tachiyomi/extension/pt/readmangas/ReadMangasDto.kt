package eu.kanade.tachiyomi.extension.pt.readmangas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

interface ResultDto {
    val mangas: List<MangaDto>
    val nextCursor: String
    val hasNextPage: Boolean
}

@Serializable
class PopularResultDto(
    @JsonNames("initialData")
    val result: MangaListDto?,
    @SerialName("mangas")
    val list: List<MangaDto> = emptyList(),
    override val nextCursor: String = result?.nextCursor ?: "",
) : ResultDto {
    override val mangas: List<MangaDto> get() = result?.mangas ?: list
    override val hasNextPage: Boolean = nextCursor.isNotBlank()
}

@Serializable
class LatestResultDto(
    @SerialName("items")
    override val mangas: List<MangaDto>,
    override val nextCursor: String = "",
    override val hasNextPage: Boolean = false,
) : ResultDto

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
