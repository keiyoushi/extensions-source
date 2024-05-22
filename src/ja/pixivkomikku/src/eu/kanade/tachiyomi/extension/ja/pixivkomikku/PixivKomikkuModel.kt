package eu.kanade.tachiyomi.extension.ja.pixivkomikku

import kotlinx.serialization.Serializable

@Serializable
internal data class Popular(
    val data: Data,
) {
    @Serializable
    internal data class Data(
        val ranking: List<RankingItem>,
    ) {
        @Serializable
        internal data class RankingItem(
            val id: Int,
            val title: String,
            val author: String,
            val main_image_url: String,
        )
    }
}

@Serializable
internal data class Latest(
    val data: Data,
) {
    @Serializable
    internal data class Data(
        val next_page_number: Int?,
        val official_works: List<OfficialWork>,
    ) {
        @Serializable
        internal data class OfficialWork(
            val id: Int,
            val name: String,
            val author: String,
            val description: String,
            val image: Image,
        ) {
            @Serializable
            internal data class Image(
                val main: String,
            )
        }
    }
}

@Serializable
internal data class Search(
    val data: Data,
) {
    @Serializable
    internal data class Data(
        val next_page_number: Int?,
        val official_works: List<OfficialWorks>,
    ) {
        @Serializable
        internal data class OfficialWorks(
            val id: Int,
            val name: String,
            val author: String,
            val description: String,
            val image: Image,
        ) {
            @Serializable
            internal data class Image(
                val main: String,
            )
        }
    }
}

@Serializable
internal data class Manga(
    val data: Data,
) {
    @Serializable
    internal data class Data(
        val official_work: OfficialWork,
    ) {
        @Serializable
        internal data class OfficialWork(
            val id: Int,
            val name: String,
            val author: String,
            val description: String,
            val image: Image,
            val categories: List<Category>,
            val tags: List<Tag>,
        ) {
            @Serializable
            internal data class Image(
                val main: String,
            )

            @Serializable
            internal data class Category(
                val name: String,
            )

            @Serializable
            internal data class Tag(
                val name: String,
            )
        }
    }
}

@Serializable
internal data class Chapters(
    val data: Data,
) {
    @Serializable
    internal data class Data(
        val episodes: List<EpisodeInfo>,
    ) {
        @Serializable
        internal data class EpisodeInfo(
            val episode: Episode?,
            val message: String?,
        )

        @Serializable
        internal data class Episode(
            val id: Int,
            val numbering_title: String,
            val sub_title: String,
            val read_start_at: Long,
        )
    }
}

@Serializable
internal data class Pages(
    val data: Data,
) {
    @Serializable
    internal data class Data(
        val reading_episode: ReadingEpisode,
    ) {
        @Serializable
        internal data class ReadingEpisode(
            val pages: List<SinglePage>,
        ) {
            @Serializable
            internal data class SinglePage(
                val url: String,
                val height: Int,
                val width: Int,
                val gridsize: Int,
            )
        }
    }
}
