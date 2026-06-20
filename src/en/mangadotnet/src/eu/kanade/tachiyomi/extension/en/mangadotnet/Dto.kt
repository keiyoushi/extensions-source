package eu.kanade.tachiyomi.extension.en.mangadotnet

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.serializer
import kotlin.collections.emptyMap
import kotlin.math.roundToInt

@Serializable
class Data<T>(
    val data: T,
)

@Serializable
class MangaList(
    @JsonNames("results", "manga_list")
    val mangaList: List<BrowseManga>? = emptyList(),
    private val pagination: Pagination? = null,
    val allGenres: List<String> = emptyList(),
) {
    fun hasNextPage() = when {
        pagination?.current != null && pagination.total != null -> pagination.current < pagination.total
        pagination?.nextCursor != null -> true
        else -> false
    }

    @Serializable
    class Pagination(
        @SerialName("total_pages")
        val total: Int? = null,
        @SerialName("current_page")
        val current: Int? = null,
        @SerialName("next_cursor")
        val nextCursor: String? = null,
    )
}

@Serializable
class ViewAllData(
    val data: MangaList,
    val allGenres: List<String> = emptyList(),
)

@Serializable
class BrowseManga(
    private val id: Int,
    private val title: String,
    private val photo: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = id.toString()
        title = this@BrowseManga.title
        thumbnail_url = photo?.let {
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

@Serializable
class MangaData(
    val mangaData: Data,
) {
    @Serializable
    class Data(
        val manga: Manga,
    )
}

@Serializable
class RelatedData(
    val suggestions: List<BrowseManga> = emptyList(),
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
    private val description: String? = null,
    private val photo: String? = null,
    private val hiatus: String? = null,
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
    fun toSManga(baseUrl: String) = SManga.create().apply {
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
        author = authors?.let {
            runCatching { it.parseAs<List<String>>().joinToString() }.getOrNull()?.let { "\u200B$it" }
        }
        artist = artists?.let {
            runCatching { it.parseAs<List<String>>().joinToString() }.getOrNull()?.let { "\u200B\u200B$it" }
        }
        genre = buildList {
            when (this@Manga.origin) {
                "JP" -> add("Manga")
                "KR" -> add("Manhwa")
                "CN" -> add("Manhua")
            }
            this@Manga.genres.forEach { add(it.trim()) }
        }.joinToString()
        status = when {
            "One Shot" in this@Manga.genres -> SManga.COMPLETED
            this@Manga.hiatus == "Yes" -> SManga.ON_HIATUS
            else -> when (this@Manga.status?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
        description = buildString {
            val rating = this@Manga.rating
            if (rating != null) {
                val stars = (rating / 2).roundToInt().coerceIn(0, 5)
                append("${"★".repeat(stars)}${"☆".repeat(5 - stars)} $rating\n\n")
            }

            val metaInfo = buildList {
                year?.let { add("**Year:** $it") }
                chapterCount?.let { add("**Chapters:** $it") }
                trackedCount?.let { add("**Tracked:** $it") }
                contentRating?.let {
                    add("**Content Rating:** ${it.replaceFirstChar { c -> c.uppercase() }}")
                }
                ratingCount?.takeIf { it > 0 }?.let { add("$it ratings") }
            }
            if (metaInfo.isNotEmpty()) {
                append(metaInfo.joinToString(" · "), "\n\n")
            }

            this@Manga.description?.let {
                append(
                    it.replace("\r\n", "\n")
                        .replace(Regex("\n{3,}"), "\n\n")
                        .replace(Regex("\n\n(-|•|\\d+\\.)"), "\n$1")
                        .trim(),
                    "\n\n",
                )
            }

            listOfNotNull(
                anilistID?.let { "[AniList](https://anilist.co/manga/$it)" },
                mangaupdatesID?.let { "[MangaUpdates](https://mangaupdates.com/series/$it)" },
                mangabakaID?.let { "[MangaBaka](https://mangabaka.org/$it)" },
                malID?.let { "[MyAnimeList](https://myanimelist.net/manga/$it)" },
                kitsuID?.let { "[Kitsu](https://kitsu.app/manga/$it)" },
                mangadexID?.let { "[MangaDex](https://mangadex.org/title/$it)" },
                sourceUrl?.let { "[Source]($it)" },
            ).also { links ->
                if (links.isNotEmpty()) {
                    append("\n**Links:**\n")
                    links.forEach { link ->
                        append("- ", link, "\n")
                    }
                }
            }

            if (altTitles.isNotEmpty()) {
                append("\n**Alternative Names:**\n")
                altTitles.forEach { altTitle ->
                    append("- ", altTitle.trim(), "\n")
                }
            }
        }.trim()
        initialized = true
    }
}

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
) {
    @Serializable
    class Group(
        val id: Int,
        val name: String,
    )
}

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
