package eu.kanade.tachiyomi.extension.ja.pixivcomic

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
        val main_image_url: String,
    )
}

@Serializable
internal class Latest(
    val next_page_number: Int?,
    val official_works: List<OfficialWork>,
)

@Serializable
internal class Manga(
    val official_work: OfficialWork,
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
        val numbering_title: String,
        val sub_title: String,
        val read_start_at: Long,
    )
}

@Serializable
internal class Pages(
    val reading_episode: ReadingEpisode,
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
