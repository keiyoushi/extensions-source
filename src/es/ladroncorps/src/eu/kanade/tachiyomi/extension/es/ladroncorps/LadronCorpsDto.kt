package eu.kanade.tachiyomi.extension.es.ladroncorps

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class AuthDto(
    @SerialName("apps")
    val tokens: Map<String, TokenDto>,
) {
    fun randomToken(): String {
        return tokens.values.random().value
    }

    @Serializable
    class TokenDto(
        @SerialName("instance")
        val value: String,
    )
}

@Serializable
class PopularMangaContainerDto(val postFeedPage: Post) {
    val posts: List<PopularMangaDto> get() = postFeedPage.posts.posts

    @Serializable
    class Post(val posts: Posts)

    @Serializable
    class Posts(val posts: List<PopularMangaDto>)
}

@Serializable
class PopularMangaDto(
    var title: String,
    @SerialName("coverMedia")
    val cover: CoverDto,
    val url: UrlDto,
) {
    @Serializable
    class CoverDto(
        @SerialName("image")
        val url: UrlDto,
    )

    /*
     * There are two fields available to get the url; when the url field is missing,
     * the path field contains the url path
     * */
    @Serializable
    class UrlDto(
        private val url: String?,
        private val path: String?,
    ) {
        override fun toString(): String {
            return url ?: path!!
        }
    }
}

@Serializable
class SearchDto(
    val posts: List<SearchMangaDto>,
)

@Serializable
class SearchMangaDto(
    var title: String,
    @SerialName("coverImage")
    val cover: CoverDto,
    private val slugs: List<String>,
) {
    val slug: String get() = slugs.first()
    val url: String get() = "/post/$slug"

    @Serializable
    class CoverDto(
        private val src: SrcDto,
    ) {
        val url: String get() = "$STATIC_MEDIA_URL/$src"
    }

    /*
     * There are two fields available to get src data; when id is missing,
     * file_name contains the src path
     * */
    @Serializable
    class SrcDto(
        private val id: String?,
        private val file_name: String?,
    ) {
        override fun toString(): String {
            return id ?: file_name!!
        }
    }

    companion object {
        const val STATIC_MEDIA_URL = "https://static.wixstatic.com/media"
    }
}
