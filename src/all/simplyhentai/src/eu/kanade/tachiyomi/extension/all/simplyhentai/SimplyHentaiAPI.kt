package eu.kanade.tachiyomi.extension.all.simplyhentai

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SHList<T>(val pagination: SHPagination, val data: T)

@Serializable
class SHPagination(val next: Int?)

@Serializable
class SHWrapper(val `object`: SHObject)

@Serializable
class SHDataAlbum(val albums: List<SHObject>)

@Serializable
class SHObject(
    private val preview: SHImage,
    private val series: SHTag,
    private val slug: String,
    private val title: String,
) {
    fun toSManga() = SManga.create().apply {
        url = "/${series.slug}/$slug"
        title = this@SHObject.title
        thumbnail_url = preview.sizes.thumb
    }
}

@Serializable
class SHImage(@SerialName("page_num") val pageNum: Int, val sizes: SHSizes)

@Serializable
class SHSizes(val full: String, val thumb: String)

@Serializable
class SHTag(val slug: String, val title: String)

@Serializable
class SHAlbum(val data: SHData)

@Serializable
class SHData(
    val artists: List<SHTag>,
    val characters: List<SHTag>,
    @SerialName("created_at") val createdAt: String,
    val description: String?,
    val preview: SHImage,
    val series: SHTag,
    private val slug: String,
    val tags: List<SHTag>,
    val title: String,
    val translators: List<SHTag>,
) {
    val path get() = "/${series.slug}/$slug"
}

@Serializable
class SHAlbumPages(val data: SHPagesData)

@Serializable
class SHPagesData(
    val pages: List<SHImage>,
)
