package eu.kanade.tachiyomi.extension.en.manhwazone

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

@Suppress("unused")
class ManhwaZone : HttpSource() {

    override val name = "ManhwaZone"
    override val baseUrl = "https://manhwazone.com"
    override val lang = "en"
    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    }

    // Site renders 24 items per page
    private val pageSize = 24

    // ── Serializable data classes ─────────────────────────────────────────────

    @Serializable
    data class LivewireResponse(
        val components: List<LivewireComponent> = emptyList(),
    )

    @Serializable
    data class LivewireComponent(
        val snapshot: String? = null,
    )

    @Serializable
    data class LivewireSnapshot(
        val data: SnapshotData = SnapshotData(),
    )

    @Serializable
    data class SnapshotData(
        // chapters[0] is the actual chapter list; each entry is a tuple whose first element is ChapterDto
        val chapters: JsonArray = JsonArray(emptyList()),
    )

    @Serializable
    data class ChapterDto(
        @SerialName("web_url") val webUrl: String? = null,
        val name: String? = null,
        val published: String? = null,
    )

    @Serializable
    data class LdJsonData(
        val author: List<LdAuthor>? = null,
    )

    @Serializable
    data class LdAuthor(
        val name: String? = null,
    )

    @Serializable
    data class RsConf(
        val p: String? = null,
        val expire: String? = null,
        val signature: String? = null,
        val tt: Int? = null,
    )

    // ── Browse ────────────────────────────────────────────────────────────────

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series?sortBy=popularity&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("article.group").map { element ->
            SManga.create().apply {
                title = element.selectFirst(".min-w-0 > a.font-semibold")!!.text()
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.attr("abs:src") ?: ""
            }
        }
        val hasNextPage = document.selectFirst("a[rel=next]") != null || mangas.size == pageSize
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/series?sortBy=latest&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("keyword", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sortBy", filter.toUriPart())
                is StatusFilter -> {
                    val status = filter.toUriPart()
                    if (status.isNotEmpty()) {
                        url.addQueryParameter("status", status)
                    }
                }
                is GenreFilterGroup -> {
                    val selectedGenres = filter.state
                        .filter { it.state }
                        .map { it.slug }
                    if (selectedGenres.isNotEmpty()) {
                        url.addQueryParameter("genres", selectedGenres.joinToString("_"))
                    }
                }
                else -> {}
            }
        }

        return GET(url.build().toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ── Manga details ─────────────────────────────────────────────────────────

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val manga = SManga.create()

        manga.title = document.selectFirst("h1.page-title")!!.text()
        manga.description = document.selectFirst("p.page-subtitle")?.text() ?: ""
        manga.thumbnail_url = document.selectFirst("img.aspect-\\[7\\/10\\], figure.relative img")?.attr("abs:src") ?: ""
        manga.genre = document.select("a.badge-genre").joinToString(", ") { it.text() }

        val statusText = document.selectFirst("span.badge-sm, span:contains(On Going), span:contains(Completed)")?.text()?.trim()
        manga.status = when (statusText?.lowercase()) {
            "on going", "ongoing", "currently publishing" -> SManga.ONGOING
            "completed", "finished" -> SManga.COMPLETED
            "on hiatus" -> SManga.ON_HIATUS
            "discontinued", "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

        val jsonLd = document.selectFirst("script[type=application/ld+json]")?.data()
        if (jsonLd != null) {
            try {
                val ldData = json.decodeFromString<LdJsonData>(jsonLd)
                val authorName = ldData.author?.firstOrNull()?.name
                if (authorName != null && authorName.lowercase() != "unknown") {
                    manga.author = authorName
                }
            } catch (_: Exception) {
                // Malformed ld+json, skip author
            }
        }

        manga.initialized = true
        return manga
    }

    // ── Chapter list ──────────────────────────────────────────────────────────

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val wireDiv = document.selectFirst("div[wire:snapshot][wire:id][wire:init=bootLoad]")
            ?: return emptyList()

        val csrfToken = document.selectFirst("meta[name=csrf-token]")?.attr("content") ?: ""
        val snapshot = wireDiv.attr("wire:snapshot")

        // Build Livewire update payload manually (structure required by the wire protocol)
        val payloadJson = """
            {
              "_token": "$csrfToken",
              "components": [{
                "snapshot": ${json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(snapshot))},
                "updates": {},
                "calls": [{"path": "", "method": "bootLoad", "params": []}]
              }]
            }
        """.trimIndent()

        val postHeaders = headersBuilder()
            .add("Accept", "application/json")
            .add("Content-Type", "application/json")
            .build()

        val postRequest = POST(
            "$baseUrl/livewire/update",
            postHeaders,
            payloadJson.toRequestBody("application/json".toMediaType()),
        )

        val postResponse = client.newCall(postRequest).execute()
        if (!postResponse.isSuccessful) return emptyList()

        val livewireResp = postResponse.parseAs<LivewireResponse>()
        val snapshotStr = livewireResp.components.firstOrNull()?.snapshot ?: return emptyList()
        val snapshotData = json.decodeFromString<LivewireSnapshot>(snapshotStr)

        // chapters[0] contains the actual chapter list; each entry is a tuple [ChapterDto, ...]
        val actualChapters = snapshotData.data.chapters.getOrNull(0)?.jsonArray ?: return emptyList()

        return actualChapters.mapNotNull { item ->
            val chapterDto = json.decodeFromString<ChapterDto>(item.jsonArray[0].toString())
            val webUrl = chapterDto.webUrl ?: return@mapNotNull null
            SChapter.create().apply {
                setUrlWithoutDomain(webUrl)
                name = chapterDto.name ?: "Chapter"
                date_upload = dateFormat.tryParse(chapterDto.published)
            }
        }.sortedByDescending { it.date_upload }
    }

    // ── Page list ─────────────────────────────────────────────────────────────

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val rsConfScript = document.selectFirst("script:containsData(__RS_CONF__)")?.data()

        if (rsConfScript != null) {
            try {
                val jsonStr = Regex(
                    """__RS_CONF__\s*=\s*(\{.*?\})\s*;""",
                    RegexOption.DOT_MATCHES_ALL,
                ).find(rsConfScript)?.groupValues?.get(1)

                if (jsonStr != null) {
                    val rsConf = json.decodeFromString<RsConf>(jsonStr)
                    if (rsConf.p != null && rsConf.expire != null && rsConf.signature != null && rsConf.tt != null) {
                        return (1..rsConf.tt).map { i ->
                            val pageStr = String.format(Locale.ENGLISH, "%03d", i)
                            val imageUrl = "https://img.mangalaxy.net/_img/${rsConf.p}/$pageStr.webp?e=${rsConf.expire}&s=${rsConf.signature}"
                            Page(i - 1, "", imageUrl)
                        }
                    }
                }
            } catch (_: Exception) {
                // Malformed __RS_CONF__, fall through to data-src fallback
            }
        }

        // Fallback to data-src if __RS_CONF__ parsing fails or is missing
        return document.select("img.lazy-image[data-src]").mapIndexed { i, element ->
            Page(i, "", element.attr("abs:data-src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ── Filters ───────────────────────────────────────────────────────────────

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        GenreFilterGroup(getGenreList()),
    )

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    class SortFilter :
        UriPartFilter(
            "Sort By",
            arrayOf(
                Pair("Popularity", "popularity"),
                Pair("Latest", "latest"),
                Pair("Rank", "rank"),
                Pair("Score", "score"),
                Pair("Follower", "follower"),
                Pair("A → Z", "name_asc"),
                Pair("Z → A", "name_desc"),
            ),
        )

    class StatusFilter :
        UriPartFilter(
            "Status",
            arrayOf(
                Pair("All Status", ""),
                Pair("Finished", "finished"),
                Pair("On Hiatus", "on_hiatus"),
                Pair("On Going", "currently_publishing"),
                Pair("Discontinued", "discontinued"),
            ),
        )

    class GenreCheckBox(name: String, val slug: String) : Filter.CheckBox(name)

    class GenreFilterGroup(genres: List<GenreCheckBox>) : Filter.Group<GenreCheckBox>("Genres", genres)

    @Suppress("SpellCheckingInspection")
    private fun getGenreList() = listOf(
        GenreCheckBox("Action", "action"),
        GenreCheckBox("Adventure", "adventure"),
        GenreCheckBox("Avant Garde", "avant-garde"),
        GenreCheckBox("Award Winning", "award-winning"),
        GenreCheckBox("Boys Love", "boys-love"),
        GenreCheckBox("Comedy", "comedy"),
        GenreCheckBox("Drama", "drama"),
        GenreCheckBox("Fantasy", "fantasy"),
        GenreCheckBox("Girls Love", "girls-love"),
        GenreCheckBox("Gourmet", "gourmet"),
        GenreCheckBox("Horror", "horror"),
        GenreCheckBox("Mystery", "mystery"),
        GenreCheckBox("Romance", "romance"),
        GenreCheckBox("Sci-Fi", "sci-fi"),
        GenreCheckBox("Slice of Life", "slice-of-life"),
        GenreCheckBox("Sports", "sports"),
        GenreCheckBox("Supernatural", "supernatural"),
        GenreCheckBox("Suspense", "suspense"),
        GenreCheckBox("Urban Fantasy", "urban-fantasy"),
        GenreCheckBox("Ecchi", "ecchi"),
        GenreCheckBox("Erotica", "erotica"),
        GenreCheckBox("Hentai", "hentai"),
        GenreCheckBox("Adult Cast", "adult-cast"),
        GenreCheckBox("Anthropomorphic", "anthropomorphic"),
        GenreCheckBox("CGDCT", "cgdct"),
        GenreCheckBox("Childcare", "childcare"),
        GenreCheckBox("Combat Sports", "combat-sports"),
        GenreCheckBox("Crossdressing", "crossdressing"),
        GenreCheckBox("Delinquents", "delinquents"),
        GenreCheckBox("Detective", "detective"),
        GenreCheckBox("Educational", "educational"),
        GenreCheckBox("Gag Humor", "gag-humor"),
        GenreCheckBox("Gore", "gore"),
        GenreCheckBox("Harem", "harem"),
        GenreCheckBox("High Stakes Game", "high-stakes-game"),
        GenreCheckBox("Historical", "historical"),
        GenreCheckBox("Idols (Female)", "idols-female"),
        GenreCheckBox("Idols (Male)", "idols-male"),
        GenreCheckBox("Isekai", "isekai"),
        GenreCheckBox("Iyashikei", "iyashikei"),
        GenreCheckBox("Love Polygon", "love-polygon"),
        GenreCheckBox("Magical Sex Shift", "magical-sex-shift"),
        GenreCheckBox("Mahou Shoujo", "mahou-shoujo"),
        GenreCheckBox("Martial Arts", "martial-arts"),
        GenreCheckBox("Mecha", "mecha"),
        GenreCheckBox("Medical", "medical"),
        GenreCheckBox("Memoir", "memoir"),
        GenreCheckBox("Military", "military"),
        GenreCheckBox("Music", "music"),
        GenreCheckBox("Mythology", "mythology"),
        GenreCheckBox("Organized Crime", "organized-crime"),
        GenreCheckBox("Otaku Culture", "otaku-culture"),
        GenreCheckBox("Parody", "parody"),
        GenreCheckBox("Performing Arts", "performing-arts"),
        GenreCheckBox("Pets", "pets"),
        GenreCheckBox("Psychological", "psychological"),
        GenreCheckBox("Racing", "racing"),
        GenreCheckBox("Reincarnation", "reincarnation"),
        GenreCheckBox("Reverse Harem", "reverse-harem"),
        GenreCheckBox("Romantic Subtext", "romantic-subtext"),
        GenreCheckBox("Samurai", "samurai"),
        GenreCheckBox("School", "school"),
        GenreCheckBox("Showbiz", "showbiz"),
        GenreCheckBox("Space", "space"),
        GenreCheckBox("Strategy Game", "strategy-game"),
        GenreCheckBox("Super Power", "super-power"),
        GenreCheckBox("Survival", "survival"),
        GenreCheckBox("Team Sports", "team-sports"),
        GenreCheckBox("Time Travel", "time-travel"),
        GenreCheckBox("Vampire", "vampire"),
        GenreCheckBox("Video Game", "video-game"),
        GenreCheckBox("Villainess", "villainess"),
        GenreCheckBox("Visual Arts", "visual-arts"),
        GenreCheckBox("Workplace", "workplace"),
        GenreCheckBox("Josei", "josei"),
        GenreCheckBox("Kids", "kids"),
        GenreCheckBox("Seinen", "seinen"),
        GenreCheckBox("Shoujo", "shoujo"),
        GenreCheckBox("Shounen", "shounen"),
    )
}
