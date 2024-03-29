package eu.kanade.tachiyomi.extension.all.simplyhentai

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
data class SHList<T>(val pagination: SHPagination, val data: T)

@Serializable
data class SHPagination(val next: Int?)

@Serializable
data class SHWrapper(val `object`: SHObject)

@Serializable
data class SHDataAlbum(val albums: List<SHObject>)

@Serializable
data class SHObject(
    val preview: SHImage,
    val series: SHTag,
    val slug: String,
    val title: String,
) {
    fun toSManga() = SManga.create().apply {
        url = "/${series.slug}/$slug"
        title = this@SHObject.title
        thumbnail_url = preview.sizes.thumb
    }
}

@Serializable
data class SHImage(val page_num: Int, val sizes: SHSizes)

@Serializable
data class SHSizes(val full: String, val thumb: String)

@Serializable
data class SHTag(val slug: String, val title: String)

@Serializable
data class SHAlbum(val data: SHData)

@Serializable
data class SHData(
    val artists: List<SHTag>,
    val characters: List<SHTag>,
    val created_at: String,
    val description: String?,
    val images: List<SHImage>,
    val preview: SHImage,
    val series: SHTag,
    val slug: String,
    val tags: List<SHTag>,
    val title: String,
    val translators: List<SHTag>,
) {
    val path by lazy { "/${series.slug}/$slug" }
}

@Serializable
data class SHAlbumPages(val data: SHPagesData)

@Serializable
data class SHPagesData(
    val pages: List<SHImage>,
)
