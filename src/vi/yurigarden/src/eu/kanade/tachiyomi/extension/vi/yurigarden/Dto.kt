package eu.kanade.tachiyomi.extension.vi.yurigarden

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class ComicsResponse(
    val comics: List<Comic>,
    val totalPages: Int,
)

@Serializable
class TrendingComic(
    val id: Int,
    val image: String,
    val title: String,
)

@Serializable
class Comic(
    val id: Int,
    val title: String,
    val thumbnail: String? = null,
)

@Serializable
class ComicDetail(
    val id: Int,
    val title: String,
    val description: String? = null,
    val status: String? = null,
    val thumbnail: String? = null,
    val authors: List<Author>,
    val genres: List<String>,
)

@Serializable
class Author(
    val name: String,
)

@Serializable
class ChapterData(
    val id: Int,
    val order: Double,
    val name: String,
    val volume: Double? = null,
    val publishedAt: Long,
    val team: Team? = null,
)

@Serializable
class Team(
    val name: String,
)

@Serializable
class ChapterDetail(
    val pages: List<PageData>,
)

@Serializable
class PageData(
    val url: String,
    val key: String? = null,
)

@Serializable
class EncryptedResponse(
    val encrypted: Boolean,
    val data: String? = null,
)

@Serializable
class SystemResources(
    val genres: Map<String, GenreResource>,
)

@Serializable
class GenreResource(
    val name: String,
    val slug: String,
)

@Serializable
class ServerFnNode(
    val s: JsonElement? = null,
    val p: ServerFnProps? = null,
)

@Serializable
class ServerFnProps(
    val k: List<String> = emptyList(),
    val v: List<ServerFnNode> = emptyList(),
)
