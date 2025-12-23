package eu.kanade.tachiyomi.extension.en.theblank

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.security.KeyPair

@Serializable
class Version(
    val version: String,
)

@Serializable
class LibraryResponse(
    val series: Series,
) {
    @Serializable
    class Series(
        val data: List<BrowseManga>,
        val meta: Meta,
    ) {
        @Serializable
        class Meta(
            @SerialName("current_page")
            val current: Int,
            @SerialName("last_page")
            val last: Int,
        )
    }
}

@Serializable
class BrowseManga(
    val slug: String,
    @JsonNames("name")
    val title: String,
    @JsonNames("cover_image")
    val image: String? = null,
) {
    fun toSManga(createThumbnailUrl: (String?) -> String?) = SManga.create().apply {
        url = slug
        title = this@BrowseManga.title
        thumbnail_url = createThumbnailUrl(image)
    }
}

@Serializable
class MangaResponse(
    val props: Props,
) {
    @Serializable
    class Props(
        val serie: Manga,
    ) {
        @Serializable
        class Manga(
            val slug: String,
            @JsonNames("name")
            val title: String,
            @JsonNames("cover_image")
            val image: String? = null,
            val description: String? = null,
            val author: String? = null,
            val artist: String? = null,
            @SerialName("name_alternative")
            val alternativeName: String? = null,
            @SerialName("release_year")
            val releaseYear: Int? = null,
            val status: String? = null,
            val type: Name? = null,
            val genres: List<Name>,
            val chapters: List<Chapter>,
        )

        @Serializable
        class Chapter(
            val slug: String,
            val title: String,
            val chapterNumber: Float,
            val createdAt: String,
            val isPremium: Boolean,
            val type: String,
        )

        @Serializable
        class Name(
            val name: String,
        )
    }
}

@Serializable
class PageListResponse(
    val props: Props,
) {
    @Serializable
    class Props(
        @SerialName("signed_urls")
        val signedUrls: List<String>,
    )
}

class KeyPairResult(val keyPair: KeyPair, val publicKeyBase64: String)

@Serializable
class SessionResponse(val sid: String)
