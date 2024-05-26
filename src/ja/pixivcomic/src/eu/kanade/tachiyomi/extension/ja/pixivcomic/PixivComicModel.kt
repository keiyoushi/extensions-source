package eu.kanade.tachiyomi.extension.ja.pixivcomic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class ApiResponse<T>(
    val data: T,
)

@Serializable
internal class Popular(
    val ranking: List<RankingItem>,
) {
    @Serializable
    internal class RankingItem(
        val id: Int,
        val title: String,
        @SerialName("main_image_url")
        val mainImageUrl: String,
    )
}

@Serializable
internal class Latest(
    @SerialName("next_page_number")
    val nextPageNumber: Int?,
    @SerialName("official_works")
    val officialWorks: List<OfficialWork>,
)

@Serializable
internal class Manga(
    @SerialName("official_work")
    val officialWork: OfficialWork,
)

@Serializable
internal class OfficialWork(
    val id: Int,
    val name: String,
    val image: Image,
    val author: String,
    val description: String,
    val categories: List<Category>?,
    val tags: List<Tag>?,
) {
    @Serializable
    internal class Category(
        val name: String,
    )

    @Serializable
    internal class Tag(
        val name: String,
    )

    @Serializable
    internal class Image(
        val main: String,
    )
}

@Serializable
internal class Chapters(
    val episodes: List<EpisodeInfo>,
) {
    @Serializable
    internal class EpisodeInfo(
        val episode: Episode?,
    )

    @Serializable
    internal class Episode(
        val id: Int,
        @SerialName("numbering_title")
        val numberingTitle: String,
        @SerialName("sub_title")
        val subTitle: String,
        @SerialName("read_start_at")
        val readStartAt: Long,
    )
}

@Serializable
internal class Pages(
    @SerialName("reading_episode")
    val readingEpisode: ReadingEpisode,
) {
    @Serializable
    internal class ReadingEpisode(
        val pages: List<SinglePage>,
    ) {
        @Serializable
        internal class SinglePage(
            val url: String,
        )
    }
}
