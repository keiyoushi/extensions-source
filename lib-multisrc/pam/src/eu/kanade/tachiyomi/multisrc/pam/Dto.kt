package eu.kanade.tachiyomi.multisrc.pam

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

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

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = SearchResponseSerializer::class)
class SearchResponse(
    val data: List<BrowseManga>,
)

// Queries under two characters are answered with a bare `[]` instead of `{"data":[]}`
object SearchResponseSerializer : JsonTransformingSerializer<SearchResponse>(SearchResponse.generatedSerializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonObject(mapOf("data" to element))
        else -> element
    }
}

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
            val uid: String,
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
            // Deferred on some sites, which then page it through /api/v1/series/{uid}/chapters
            val chapters: List<Chapter>? = null,
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
        /** Handed to the site's attestation as is. */
        val attestation: JsonObject? = null,
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

/** What reader.js is called with. */
@Serializable
class ReaderInput(
    private val bridge: String,
    private val csrfToken: String,
    private val sharedUrl: String,
    private val glueUrl: String,
    private val exports: Map<String, String>,
    private val attestation: JsonObject,
    private val serverPubkey: String,
    private val privateKey: String,
    private val clientPubkey: String,
    private val uid: String,
    private val manifestVersion: Int,
    private val chapterUrl: String,
    private val reloadHeaders: Map<String, String>,
)

/** What reader.js hands back. */
@Serializable
class ReaderResult(
    val token: String? = null,
    val manifest: ManifestResponse? = null,
    val contentKey: String? = null,
    val error: String? = null,
)

@Serializable
class ManifestResponse(
    val base: String,
    val hint: String,
    val count: Int,
    val variants: List<Int> = emptyList(),
    /** Page ticket, sent as `X-Pt` with every page. */
    val pt: String? = null,
)

@Serializable
class ChapterListResponse(
    val items: List<ChapterListItem>,
    @SerialName("last_page")
    val lastPage: Int,
)

@Serializable
class ChapterListItem(
    private val slug: String,
    private val title: String,
    private val type: String,
    @SerialName("free_at")
    private val freeAt: String? = null,
    @SerialName("created_at")
    private val createdAt: String,
) {
    /** Premium chapters turn public at [freeAt]. */
    fun toChapter(isFree: (String) -> Boolean) = MangaResponse.Props.Chapter(
        slug = slug,
        title = title,
        createdAt = createdAt,
        isPremium = type == "premium" && freeAt?.let(isFree) != true,
    )
}
