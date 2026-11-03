package eu.kanade.tachiyomi.extension.all.mangadotnet

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.serializer

@Serializable
class Data<T>(
    val data: T,
)

@Serializable
class MangaList(
    @JsonNames("results", "manga_list")
    val mangaList: List<BrowseManga>? = emptyList(),
    private val pagination: Pagination? = null,
    val facets: FacetsData? = null,
) {
    fun hasNextPage() = when {
        pagination?.current != null && pagination.total != null -> pagination.current < pagination.total
        !pagination?.nextCursor.isNullOrEmpty() -> true
        else -> false
    }

    @Serializable
    class Pagination(
        @SerialName("total_pages")
        @JsonNames("totalPages", "last_page", "lastPage")
        val total: Int? = null,
        @SerialName("current_page")
        @JsonNames("currentPage", "page")
        val current: Int? = null,
        @SerialName("next_cursor")
        @JsonNames("nextCursor", "cursor")
        val nextCursor: String? = null,
        @SerialName("per_page")
        val perPage: Int? = null,
        @SerialName("total_results")
        val totalResults: Int? = null,
    )
}

@Serializable
class ViewAllData(
    val data: MangaList,
    val allGenres: List<String> = emptyList(),
)

@Serializable
class BrowseManga(
    @SerialName("manga_id")
    @JsonNames("id")
    val id: Int,
    private val title: String,
    private val photo: String? = null,
    @SerialName("is_blurworthy")
    private val isBlurworthy: JsonElement? = null,
) {
    val isAdult: Boolean
        get() = when (isBlurworthy) {
            is JsonPrimitive -> isBlurworthy.intOrNull == 1 || isBlurworthy.booleanOrNull == true
            else -> false
        }

    fun toSManga(baseUrl: String, hideAdultCovers: Boolean = false) = SManga.create().apply {
        url = id.toString()
        title = this@BrowseManga.title

        thumbnail_url = if (isAdult && hideAdultCovers) {
            "https://fakeimg.cnbattle.com/400x600/?text=NSFW"
        } else {
            photo?.let {
                if (it.startsWith("/")) {
                    baseUrl + it
                } else if (it.startsWith("http")) {
                    it
                } else {
                    null
                }
            }
        }
    }
}

@Serializable
class BookmarksData(
    val entries: List<BrowseManga> = emptyList(),
    val total: Int? = null,
    val page: Int? = null,
    @SerialName("per_page")
    val perPage: Int? = null,
) {
    fun hasNextPage(): Boolean {
        val p = page ?: 1
        val pp = perPage ?: 39
        val t = total ?: 0
        return p * pp < t
    }
}

@Serializable
class MangaData(
    val mangaData: Data,
) {
    @Serializable
    class Data(
        val manga: Manga,
        @SerialName("total_volumes")
        val volumeCount: Int? = null,
    )
}

@Serializable
class RelatedData(
    val suggestions: List<BrowseManga>? = null,
    val relationsData: RelationsData? = null,
) {
    @Serializable
    class RelationsData(
        @Serializable(RelationsMapSerializer::class)
        val relations: Map<String, List<BrowseManga>> = emptyMap(),
    )

    internal object RelationsMapSerializer : JsonTransformingSerializer<Map<String, List<BrowseManga>>>(
        serializer<Map<String, List<BrowseManga>>>(),
    ) {
        override fun transformDeserialize(element: JsonElement): JsonElement {
            if (element is JsonArray) return JsonObject(emptyMap())
            return element
        }
    }
}

@Serializable
class Manga(
    private val id: Int,
    private val title: String,
    private val genres: List<String> = emptyList(),
    private val tags: List<TagCategory> = emptyList(),
    private val description: String? = null,
    private val photo: String? = null,
    private val status: String? = null,
    @SerialName("source_url")
    private val sourceUrl: String? = null,
    @SerialName("alt_titles")
    private val altTitles: List<String> = emptyList(),
    @SerialName("country_of_origin")
    private val origin: String? = null,
    @SerialName("avg_rating")
    private val rating: Float? = null,
    @SerialName("anilist_id")
    private val anilistID: Long? = null,
    @SerialName("mangaupdates_id")
    private val mangaupdatesID: String? = null,
    @SerialName("mangabaka_id")
    private val mangabakaID: Long? = null,
    @SerialName("mal_id")
    private val malID: Long? = null,
    @SerialName("kitsu_id")
    private val kitsuID: Long? = null,
    @SerialName("anime_planet_id")
    private val animePlanetId: String? = null,
    @SerialName("shikimori_id")
    private val shikimoriId: Long? = null,
    @SerialName("ann_id")
    private val animeNewsNetworkId: Long? = null,
    @SerialName("mangadex_id")
    private val mangadexID: String? = null,
    @SerialName("year")
    private val year: Int? = null,
    @SerialName("chapter_count")
    private val chapterCount: Int? = null,
    @SerialName("tracked_count")
    private val trackedCount: Int? = null,
    @SerialName("rating_count")
    private val ratingCount: Int? = null,
    @SerialName("content_rating")
    private val contentRating: String? = null,
    private val authors: String? = null,
    private val artists: String? = null,
) {
    companion object {
        private val multipleNewlinesRegex = Regex("\n{3,}")
        private val listRegex = Regex("\n\n(-|•|\\d+\\.)")
    }

    fun toSManga(baseUrl: String, showTags: Boolean = true, extraVolumeCount: Int? = null) = SManga.create().apply {
        url = id.toString()
        title = this@Manga.title
        thumbnail_url = photo?.let {
            if (it.startsWith("/")) {
                baseUrl + it
            } else if (it.startsWith("http")) {
                it
            } else {
                null
            }
        }
        author = authors?.let { it ->
            runCatching { it.parseAs<List<String>>().joinToString() }.getOrNull()?.let { "\u200B$it" }
        }
        artist = artists?.let { it ->
            runCatching { it.parseAs<List<String>>().joinToString() }.getOrNull()?.let { "\u200B\u200B$it" }
        }
        genre = buildList {
            when (this@Manga.origin) {
                "JP" -> add("Manga")
                "KR" -> add("Manhwa")
                "CN" -> add("Manhua")
                "EN" -> add("OEL")
            }
            this@Manga.genres.forEach { add(it.trim()) }
            if (showTags) {
                this@Manga.tags.flatMap { it.tags }
                    .map { it.name.trim() }
                    .sortedBy { it.lowercase() }
                    .forEach { add(it) }
            }
        }.joinToString()
        status = when {
            else -> when (this@Manga.status?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "cancelled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
        description = MarkdownDescription.Builder().apply {
            rating(this@Manga.rating, votes = ratingCount?.takeIf { it > 0 })
            field("Year", year?.toString())
            field("Chapters", chapterCount?.toString())
            field("Volumes", extraVolumeCount?.takeIf { it > 0 }?.toString())
            field("Tracked", trackedCount?.toString())
            field("Content Rating", contentRating?.replaceFirstChar { it.uppercase() })
            markdown(
                this@Manga.description?.replace("\r\n", "\n")
                    ?.replace(multipleNewlinesRegex, "\n\n")
                    ?.replace(listRegex, "\n$1"),
            )
            links(
                listOf(
                    "AniList" to anilistID?.let { "https://anilist.co/manga/$it" },
                    "MangaUpdates" to mangaupdatesID?.let { "https://www.mangaupdates.com/series/$it" },
                    "MangaBaka" to mangabakaID?.let { "https://mangabaka.org/$it" },
                    "MyAnimeList" to malID?.let { "https://myanimelist.net/manga/$it" },
                    "Kitsu" to kitsuID?.let { "https://kitsu.app/manga/$it" },
                    "AnimePlanet" to animePlanetId?.let { "https://www.anime-planet.com/manga/$it" },
                    "Shikimori" to shikimoriId?.let { "https://shikimori.io/mangas/$it" },
                    "AnimeNewsNetwork" to animeNewsNetworkId?.let { "https://www.animenewsnetwork.com/encyclopedia/manga.php?id=$it" },
                    "MangaDex" to mangadexID?.let { "https://mangadex.org/title/$it" },
                    "Source" to sourceUrl,
                ),
            )
            altTitles(altTitles)
        }.build()
        initialized = true
    }
}

@Serializable
class Group(
    val id: Int,
    val name: String,
)

@Serializable
class Chapter(
    val id: Int,
    @SerialName("chapter_number")
    val number: Float? = null,
    @SerialName("volume_number")
    val volume: Float? = null,
    @SerialName("chapter_title")
    val name: String? = null,
    val language: String? = null,
    @SerialName("group_id")
    val groupId: Int? = null,
    @SerialName("group_name")
    val group: String? = null,
    @SerialName("scanlator_name")
    val scanlator: String? = null,
    @SerialName("date_added")
    val date: String? = null,
    @SerialName("page_count")
    val pageCount: Int? = null,
    val source: String = "user",
    val groups: List<Group> = emptyList(),
)

@Serializable
class Volume(
    val id: Int,
    @SerialName("volume_number")
    val volume: Float? = null,
    @SerialName("group_name")
    val group: String? = null,
    @SerialName("scanlator_name")
    val scanlator: String? = null,
    @SerialName("date_added")
    val date: String? = null,
    val source: String = "user",
    val language: String? = null,
    val groups: List<Group> = emptyList(),
)

@Serializable
class ChapterUrl(
    val id: String,
    val source: String,
    val isVolume: Boolean,
)

@Serializable
class Images(
    val manga: MangaId,
    val images: List<Image>,
) {
    @Serializable
    class MangaId(
        val id: Int,
    )

    @Serializable
    class Image(
        val url: String,
    )
}

@Serializable
class TagCategory(
    val category: String,
    @SerialName("is_adult")
    val isAdult: Boolean = false,
    val tags: List<TagItem> = emptyList(),
)

@Serializable
class TagItem(
    val name: String,
    val weight: String? = null,
    @SerialName("is_adult")
    val isAdult: Boolean = false,
)

@Serializable
class MangaItemsResponse(
    val items: List<BrowseManga> = emptyList(),
)

@Serializable
class FacetsData(
    val genres: List<FacetItem> = emptyList(),
    val tags: List<FacetTagItem> = emptyList(),
)

@Serializable
class FacetItem(
    val key: String,
    val count: Int? = null,
)

@Serializable
class FacetTagItem(
    val key: String,
    val count: Int? = null,
    @SerialName("is_adult")
    val isAdult: Boolean = false,
)

@Serializable
class TagsResponse(
    val categories: List<TagCategory> = emptyList(),
)
