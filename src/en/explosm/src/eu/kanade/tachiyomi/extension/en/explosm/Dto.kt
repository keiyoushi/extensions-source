package eu.kanade.tachiyomi.extension.en.explosm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ComicsResponse(
    val pageProps: PageProps,
)

@Serializable
class PageProps(
    val comicArchiveData: Map<String, Map<String, List<ComicDto>>>,
)

@Serializable
class ComicDto(
    val slug: String,
    val file: String?,
    @SerialName("file_static") val fileStatic: String?,
    @SerialName("publish_at") val publishAt: String,
    @SerialName("author_name") val authorName: String?,
)
