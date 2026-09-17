package eu.kanade.tachiyomi.multisrc.pam

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

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
        val meta: Meta? = null,
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
class SearchResponse(
    val data: List<BrowseManga>,
)

@Serializable
class BrowseManga(
    private val slug: String,
    @JsonNames("name")
    private val title: String,
    @JsonNames("cover_image")
    private val image: String? = null,
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
            val createdAt: String,
            val isPremium: Boolean,
        )

        @Serializable
        class Name(
            val name: String,
        )
    }
}

@Serializable
class PageListResponse(
    val component: String,
    val version: String,
    val props: Props,
) {
    @Serializable
    class Props(
        @SerialName("page_count")
        val pageCount: Int = 0,
        @SerialName("chapter_token")
        val chapterToken: String? = null,
        @SerialName("server_pubkey")
        val serverPubkey: String,
        @SerialName("reader_v2")
        val readerV2: Boolean = false,
        val attestation: Attestation? = null,
        val data: Data,
    ) {
        @Serializable
        class Data(
            val uid: String,
            val slug: String,
            val serie: Serie,
        )

        @Serializable
        class Serie(val slug: String)
    }
}

@Serializable
class Attestation(
    val challenge: String,
    @SerialName("webgl_seed")
    val webglSeed: String,
)

/** Partial Inertia reload, asking only for the freshly minted token and challenge. */
@Serializable
class AttestationReload(
    val props: Props,
) {
    @Serializable
    class Props(
        @SerialName("chapter_token")
        val chapterToken: String? = null,
        val attestation: Attestation? = null,
    )
}

@Serializable
class AttestationResponse(
    val ct: String? = null,
)

@Serializable
class ManifestResponse(
    val base: String,
    val hint: String,
    val count: Int,
    val variants: List<Int> = emptyList(),
)

@Serializable
class AttestationRequest(
    val c: String,
    val v: String,
    val sp: String,
    val d: String,
    val pk: String,
)

@Serializable
class ManifestRequest(
    val v: Int,
    val c: String,
    val t: String,
    val ts: Long,
    val n: String,
    val s: String,
)
