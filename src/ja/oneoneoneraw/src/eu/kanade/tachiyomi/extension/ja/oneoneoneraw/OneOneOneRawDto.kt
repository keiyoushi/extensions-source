package eu.kanade.tachiyomi.extension.ja.oneoneoneraw

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ListDto(
    val data: List<EntryDto>,
    val page: PageDto,
) {
    @Serializable
    data class EntryDto(
        @SerialName("id") val mangaId: Int,
        val name: String,
        val slug: String,
        val description: String? = null,
        val status: String? = null,
        val image: String? = null,
        val taxonomy: JsonElement,
        @SerialName("alternative_name") val altName: String? = null,
    )

    @Serializable
    data class PageDto(
        @SerialName("current_page") val currentPage: Int,
        @SerialName("last_page") val lastPage: Int,
    )
}

@Serializable
data class TaxonomyDto(
    val genres: List<GenreDto>,
) {
    @Serializable
    data class GenreDto(
        val name: String,
        val type: String,
    )
}

@Serializable
data class ChapterListDto(
    val data: List<ChapterDto>,
) {
    @Serializable
    data class ChapterDto(
        @SerialName("id") val chapterId: Int,
        val name: String,
        val slug: String,
        val index: String,
        @SerialName("created_at") val createdAt: String? = null,
    )
}

@Serializable
data class PagesDto(
    val props: PropsDto,
) {
    @Serializable
    data class PropsDto(
        val pageProps: PagePropsDto,
    ) {
        @Serializable
        data class PagePropsDto(
            val initialChapter: InitialChapterDto,
        ) {
            @Serializable
            data class InitialChapterDto(
                val images: List<ImageDto>,
            ) {
                @Serializable
                data class ImageDto(
                    val index: Int,
                    val url: String,
                )
            }
        }
    }
}
