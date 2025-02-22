package eu.kanade.tachiyomi.extension.all.simplycosplay

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.Serializable
import java.util.Locale

typealias browseResponse = Data<List<BrowseItem>>

typealias detailsResponse = Data<DetailsResponse>

typealias pageResponse = Data<PageResponse>

@Serializable
data class Data<T>(val data: T)

@Serializable
data class BrowseItem(
    val title: String? = null,
    val slug: String,
    val type: String,
    val preview: Images,
) {
    fun toSManga() = SManga.create().apply {
        title = this@BrowseItem.title ?: ""
        url = "/${type.lowercase().trim()}/new/$slug"
        thumbnail_url = preview.urls.thumb.url
        description = preview.publish_date?.let { "Date: $it" }
    }
}

@Serializable
data class TagsResponse(
    val aggs: Agg,
)

@Serializable
data class Agg(
    val tag_names: TagNames,
)

@Serializable
data class TagNames(
    val buckets: List<GenreItem>,
)

@Serializable
data class GenreItem(
    val key: String,
)

@Serializable
data class DetailsResponse(
    val title: String? = null,
    val slug: String,
    val type: String,
    val preview: Images,
    val tags: List<Tag>? = emptyList(),
    val image_count: Int? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = this@DetailsResponse.title ?: ""
        url = "/${type.lowercase().trim()}/new/$slug"
        thumbnail_url = preview.urls.thumb.url
        genre = tags?.mapNotNull { it ->
            it.name?.trim()?.split(" ")?.let { genre ->
                genre.map {
                    it.replaceFirstChar { char ->
                        if (char.isLowerCase()) {
                            char.titlecase(
                                Locale.ROOT,
                            )
                        } else {
                            char.toString()
                        }
                    }
                }
            }?.joinToString(" ")
        }?.joinToString()
        description = buildString {
            append("Type: $type\n")
            image_count?.let { append("Images: $it\n") }
            preview.publish_date?.let { append("Date: $it\n") }
        }
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        status = SManga.COMPLETED
    }
}

@Serializable
data class PageResponse(
    val images: List<Images>? = null,
    val preview: Images,
)

@Serializable
data class Images(
    val publish_date: String? = null,
    val urls: Urls,
)

@Serializable
data class Urls(
    val url: String? = null,
    val thumb: Url,
)

@Serializable
data class Url(
    val url: String? = null,
)

@Serializable
data class Tag(
    val name: String? = null,
)
