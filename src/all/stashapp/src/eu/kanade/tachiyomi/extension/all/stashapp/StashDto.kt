package eu.kanade.tachiyomi.extension.all.stashapp

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ===== StashApp Requests/Responses =====

@Serializable
class MangaBriefVariables(
    val filter: FindFilterType,
)

@Serializable
class MangaBriefData(
    val findGalleries: FindGalleriesResultType,
)

@Serializable
class MangaDetailsVariables(
    val id: String,
)

@Serializable
class MangaDetailsData(
    val findGallery: Gallery,
)

@Serializable
class ChapterListVariables(
    val id: String,
)

@Serializable
class ChapterListData(
    val findGallery: Gallery,
)

@Serializable
class PageListVariables(
    val id: Int,
)

@Serializable
class PageListData(
    val findImages: FindImagesResultType,
)

// ===== StashApp Types =====

@Serializable
enum class SortDirectionEnum {
    ASC,
    DESC,
}

@Serializable
class FindFilterType(
    val q: String? = null,
    val page: Int? = null,
    /**
     * use per_page = -1 to indicate all results. Defaults to 25.
     */
    @SerialName("per_page")
    val perPage: Int? = null,
    val sort: String? = null,
    val direction: SortDirectionEnum? = null,
)

@Serializable
class Folder(
    val path: String? = null,
)

@Serializable
class Tag(
    val name: String = "",
)

@Serializable
class ImagePathsType(
    val thumbnail: String? = null,
)

@Serializable
class VisualFile(
    @SerialName("__typename")
    val typename: String? = null,
)

@Serializable
class Image(
    val id: String? = null,
    val paths: ImagePathsType? = null,
    @SerialName("visual_files")
    val visualFiles: List<VisualFile> = emptyList(),
)

@Serializable
class Gallery(
    val id: String? = null,
    val title: String? = null,
    val folder: Folder? = null,
    val photographer: String? = null,
    val details: String? = null,
    val tags: List<Tag>? = null,
    val cover: Image? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
)

@Serializable
class FindGalleriesResultType(
    val count: Int? = null,
    val galleries: List<Gallery>? = null,
)

@Serializable
class FindImagesResultType(
    val count: Int? = null,
    val megapixels: Float? = null,
    @SerialName("filesize")
    val fileSize: Float? = null,
    val images: List<Image>? = null,
)

// ===== Extension Mapping & Utilities =====

internal fun Gallery.toMangaBrief(baseUrl: String): SManga? {
    val galleryId = id?.takeIf(String::isNotBlank) ?: return null

    return SManga.create().apply {
        url = toAbsoluteUrl(baseUrl, "/galleries/$galleryId")
        title = toTitle()
        thumbnail_url = cover?.toThumbnailUrl()
    }
}

internal fun Gallery.toMangaDetails(baseUrl: String): SManga? {
    val galleryId = id?.takeIf(String::isNotBlank) ?: return null

    return SManga.create().apply {
        url = toAbsoluteUrl(baseUrl, "/galleries/$galleryId")
        title = toTitle()
        artist = photographer?.takeIf(String::isNotBlank)
        author = artist
        description = details?.takeIf(String::isNotBlank)
        genre = tags
            .orEmpty()
            .mapNotNull(Tag::name)
            .filter(String::isNotBlank)
            .joinToString()
            .takeIf(String::isNotBlank)
        status = SManga.UNKNOWN
        thumbnail_url = cover?.toThumbnailUrl()
        update_strategy = UpdateStrategy.ALWAYS_UPDATE
        initialized = true
    }
}

internal fun Image.toPage(index: Int, baseUrl: String): Page? {
    val imageId = id?.takeIf(String::isNotBlank) ?: return null
    return Page(
        index = index,
        url = toAbsoluteUrl(baseUrl, "/images/$imageId"),
        imageUrl = toAbsoluteUrl(baseUrl, "/image/$imageId/image"),
    )
}

internal fun urlLast(url: String): String = url.substringBefore('?')
    .substringBefore('#')
    .let(::pathLast)

internal fun toAbsoluteUrl(baseUrl: String, path: String): String = when {
    path.startsWith("http://") || path.startsWith("https://") -> path
    path.startsWith("/") -> "$baseUrl$path"
    else -> "$baseUrl/$path"
}

private fun Gallery.toTitle(): String = title?.takeIf(String::isNotBlank)
    ?: folder?.path?.takeIf(String::isNotBlank)?.let(::pathLast)
    ?: id!!

private fun Image.toThumbnailUrl(): String? {
    if (visualFiles.firstOrNull()?.isImage() != true) return null

    return paths
        ?.thumbnail
        ?.takeIf(String::isNotBlank)
}

private fun VisualFile.isImage(): Boolean = typename == "ImageFile"

private fun pathLast(path: String): String = path.trimEnd('/', '\\')
    .substringAfterLast('/')
    .substringAfterLast('\\')
