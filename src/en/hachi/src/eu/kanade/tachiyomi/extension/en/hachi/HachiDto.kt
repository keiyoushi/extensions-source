package eu.kanade.tachiyomi.extension.en.hachi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// Responses
// @Serializable
// class TagResponseDto(
//    val currentPage: Int,
//    val size: Int,
//    val tags: List<TagDto>,
//    val totalItems: Int,
//    val totalPages: Int,
// ) {
//    @Serializable
//    class TagDto(
//        val articleCount: Int,
//        val id: Int,
//        val name: String,
//    )
// }

@Serializable
class ArticleResponseDto(
    val content: List<ArticleDto>,
//    val empty: Boolean,
//    val first: Boolean,
    val last: Boolean,
//    val number: Int,
//    val numberOfElements: Int,
//    val pageable: PageableDto,
//    val size: Int,
//    val sort: SortDto,
//    val totalElements: Int,
//    val totalPages: Int,
) {
//    @Serializable
//    class PageableDto(
//        val offset: Int,
//        val pageNumber: Int,
//        val pageSize: Int,
//        val paged: Boolean,
//        val sort: SortDto,
//        val unpaged: Boolean,
//    )
}

@Serializable
class DetailsResponseDto(
//    @SerialName("__N_SSG")
//    val nSSG: Boolean,
    val pageProps: PagePropsDto,
) {
    @Serializable
    class PagePropsDto(
        val article: ArticleDto,
        val chapters: List<ChapterDto>,
//        val comicSeries: ComicSeriesDto,
//        val metaTags: List<MetaTagDto>,
//        val moreLikeArticles: List<ArticleDto>,
//        val ratings: RatingsDto,
//        val stats: StatsDto,
//        val title: String,
    ) {
//        @Serializable
//        class ComicSeriesDto(
//            val alternativeHeadline: String,
//            val artist: ArtistDto,
//            val author: AuthorDto,
//            @SerialName("@context")
//            val context: String,
//            val genre: String,
//            val name: String,
//            @SerialName("@type")
//            val type: String,
//            val url: String,
//        ) {
//            @Serializable
//            class ArtistDto(
//                val name: String,
//                @SerialName("@type")
//                val type: String,
//            )
//
//            @Serializable
//            class AuthorDto(
//                val name: String,
//                @SerialName("@type")
//                val type: String,
//            )
//        }
//
//        @Serializable
//        class RatingsDto(
//            val averageRating: Float,
//            val id: Int,
//            val ratingCounts: List<RatingCountDto>,
//            val totalRatingCount: Int,
//        ) {
//            @Serializable
//            class RatingCountDto(
//                val count: Int,
//                val rating: Float,
//            )
//        }
//
//        @Serializable
//        class StatsDto(
//            val allTimeViews: Int? = null,
//            val id: Int? = null,
//            val libraryEntryCounts: List<LibraryEntryCountDto>? = null,
//            val monthlyViews: Int? = null,
//            val rank: Int? = null,
//            val totalLibraryEntries: Int? = null,
//            val weeklyViews: Int? = null,
//        ) {
//            @Serializable
//            class LibraryEntryCountDto(
//                val count: Int,
//                val status: String,
//            )
//        }
    }
}

@Serializable
class ChapterResponseDto(
//    @SerialName("__N_SSG")
//    val nSSG: Boolean,
    val pageProps: PagePropsDto,
) {
    @Serializable
    class PagePropsDto(
//        val chapter: ChapterFullDto,
        val images: List<String>,
//        val metaTags: List<MetaTagDto>,
    ) {
//        @Serializable
//        class ChapterFullDto(
//            val alternativeTitles: List<AlternativeTitleDto>,
//            val articleId: Int,
//            val articleType: String,
//            val articleUrl: String,
//            val chapterNumber: Float,
//            val createdAt: String,
//            val id: Int,
//            val imageLinks: List<String>,
//            val mature: Boolean,
//            val nextChapterNumber: Float,
//            val previousChapterNumber: Float,
//            val title: String,
//            val totalChapters: Float,
//        )
    }
}

// Common
@Serializable
class ChapterDto(
    val chapterNumber: Float,
    val createdAt: String,
//    val id: Int,
//    val views: Int,
)

// @Serializable
// class MetaTagDto(
//    val content: String,
//    val `property`: String,
// )

// @Serializable
// class AlternativeTitleDto(
//    val language: String,
//    val title: String,
// )

// @Serializable
// class SortDto(
//    val empty: Boolean,
//    val sorted: Boolean,
//    val unsorted: Boolean,
// )

// @Serializable
// class ExternalLinkDto(
//    val externalApp: ExternalAppDto,
//    val externalId: String,
//    val id: Int,
// ) {
//    @Serializable
//    class ExternalAppDto(
//        val domain: String,
//        val id: Int,
//        val name: String,
//        val path: String,
//    )
// }

@Serializable
class ArticleDto(
//    val alternativeTitles: List<AlternativeTitleDto>,
    @Serializable(with = MissingFieldSerializer::class)
    val artist: String?,
    @Serializable(with = MissingFieldSerializer::class)
    val author: String?,
//    val chapters: List<ChapterDto>,
    val coverImage: String,
//    val createdAt: String,
//    val externalLinks: List<ExternalLinkDto>,
//    val id: Int,
//    val imagePath: String? = null,
    val link: String,
//    val maintainer: String? = null,
//    val mature: Boolean,
//    val originalLink: String? = null,
//    val poster: String? = null,
//    val rating: Float,
    val status: String,
    val summary: String,
    val tags: List<String>,
    val title: String,
//    val totalChapters: Float,
//    val type: String,
//    val updatedAt: String,
//    val views: Int,
)

// Partial
@Serializable
class NextDataDto(
    val buildId: String,
)

// Serializers
object MissingFieldSerializer : KSerializer<String?> {
    override val descriptor = buildClassSerialDescriptor("MissingField")

    override fun deserialize(decoder: Decoder): String? {
        return decoder.decodeString().takeIf { it != "N/A" }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        encoder.encodeString(value ?: "N/A")
    }
}
