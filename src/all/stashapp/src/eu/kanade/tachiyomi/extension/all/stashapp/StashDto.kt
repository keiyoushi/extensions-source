package eu.kanade.tachiyomi.extension.all.stashapp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ===== GraphQL Types =====

/**
 * [graphql over http](https://graphql.org/learn/serving-over-http)
 */
@Serializable
data class GraphQLRequest<T>(
    val query: String,
    val operationName: String? = null,
    val variables: T? = null,
    val extensions: JsonObject? = null,
)

@Serializable
data class GraphQLErrorLocation(
    val line: Int,
    val column: Int,
)

@Serializable
data class GraphQLError(
    val message: String,
    val locations: List<GraphQLErrorLocation>? = null,
    val path: List<JsonElement>? = null,
    val extensions: JsonObject? = null,
)

/**
 * [graphql over http](https://graphql.org/learn/serving-over-http)
 */
@Serializable
data class GraphQLResponse<T>(
    val data: T? = null,
    val errors: List<GraphQLError>? = null,
    val extensions: JsonObject? = null,
)

// ===== StashApp Requests/Responses =====

@Serializable
data class MangaBriefVariables(
    val filter: FindFilterType,
)

@Serializable
data class MangaBriefData(
    val findGalleries: FindGalleriesResultType,
)

@Serializable
data class MangaDetailsVariables(
    val id: String,
)

@Serializable
data class MangaDetailsData(
    val findGallery: Gallery,
)

@Serializable
data class ChapterListVariables(
    val id: String,
)

@Serializable
data class ChapterListData(
    val findGallery: Gallery,
)

@Serializable
data class PageListVariables(
    val id: Int,
)

@Serializable
data class PageListData(
    val findImages: FindImagesResultType,
)

// ===== StashApp Types =====

@Serializable
enum class SortDirectionEnum {
    ASC,
    DESC,
}

@Serializable
data class FindFilterType(
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

/**
 * There is more field, ignored because not used
 */
@Serializable
data class Folder(
    val path: String? = null,
)

/**
 * There is more field, ignored because not used
 */
@Serializable
data class Tag(
    val name: String = "",
)

/**
 * There is more field, ignored because not used
 */
@Serializable
data class ImagePathsType(
    val thumbnail: String? = null,
)

/**
 * There is more field, ignored because not used
 */
@Serializable
data class VisualFile(
    @SerialName("__typename")
    val __typename: String? = null,
)

/**
 * There is more field, ignored because not used
 */
@Serializable
data class Image(
    val id: String? = null,
    val paths: ImagePathsType? = null,
    @SerialName("visual_files")
    val visualFiles: List<VisualFile> = emptyList(),
)

/**
 * There is more field, ignored because not used
 */
@Serializable
data class Gallery(
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
data class FindGalleriesResultType(
    val count: Int? = null,
    val galleries: List<Gallery>? = null,
)

@Serializable
data class FindImagesResultType(
    val count: Int? = null,
    /**
     * Total megapixels of the images
     */
    val megapixels: Float? = null,
    /**
     * Total file size in bytes
     */
    @SerialName("filesize")
    val fileSize: Float? = null,

    val images: List<Image>? = null,
)
