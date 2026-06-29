package eu.kanade.tachiyomi.extension.vi.yurigarden

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class ComicsResponse(
    val comics: List<Comic> = emptyList(),
    val totalPages: Int = 0,
)

@Serializable
data class TrendingComic(
    val id: Int,
    val image: String = "",
    val title: String,
    val value: Int = 0,
    val rank: Int = 0,
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
    val authors: List<Author> = emptyList(),
    val genres: List<String> = emptyList(),
)

@Serializable
class Author(
    val name: String,
)

@Serializable
class ChapterData(
    val id: Int,
    val order: Double,
    val name: String = "",
    val volume: Double? = null,
    val publishedAt: Long = 0L,
    val lastUpdated: Long? = null,
    val team: Team? = null,
)

@Serializable
class Team(
    val id: Int? = null,
    val name: String = "",
)

@Serializable
class ChapterDetail(
    val pages: List<PageData> = emptyList(),
)

@Serializable
class PageData(
    val url: String,
    val key: String? = null,
)

@Serializable
class EncryptedResponse(
    val encrypted: Boolean = false,
    val data: String? = null,
)

@Serializable
class UserAuthRequest(
    val email: String,
    val name: String,
    val avatar: String,
    val token: String,
)

@Serializable
class UserAuthResponse(
    val accessToken: String,
)

@Serializable
class WebViewAuthData(
    val email: String? = null,
    val displayName: String? = null,
    val photoURL: String? = null,
    val stsTokenManager: WebViewTokenManager? = null,
)

@Serializable
class WebViewTokenManager(
    val accessToken: String? = null,
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
