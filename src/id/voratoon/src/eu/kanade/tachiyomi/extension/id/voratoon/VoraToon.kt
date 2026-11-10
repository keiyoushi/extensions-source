package eu.kanade.tachiyomi.extension.id.voratoon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Source
abstract class VoraToon : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
    }

    override fun Headers.Builder.configureHeaders() = apply {
        set("Referer", "$baseUrl/")
    }

    private val rscHeaders: Headers
        get() = headers.newBuilder().set("RSC", "1").build()

    override suspend fun getPopularManga(page: Int): MangasPage = parseSeriesList(
        "$baseUrl/browse".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "popular")
            .build(),
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseSeriesList(
        "$baseUrl/browse".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "new")
            .build(),
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/browse".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotEmpty()) {
                addQueryParameter("q", query)
            }
            filters.filterIsInstance<UriQueryFilter>().forEach {
                it.addToQuery(this)
            }
        }.build()

        return parseSeriesList(url)
    }

    private suspend fun parseSeriesList(url: HttpUrl): MangasPage {
        val dto = client.get(url, rscHeaders, Fetch.XHR).extractNextJs<BrowseDto> {
            it is JsonObject && "series" in it && "seriesMeta" in it
        } ?: return MangasPage(emptyList(), false)

        val mangas = dto.series.map { it.toSManga() }
        val hasNextPage = dto.seriesMeta?.let { (it.page ?: 0) < (it.lastPage ?: 0) } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.getSlug(baseUrl)
        val dto = client.get("$baseUrl/series/$slug", rscHeaders, Fetch.XHR).extractNextJs<SeriesDetailsDto> {
            it is JsonObject && "series" in it && "chapters" in it
        } ?: return SMangaUpdate(manga, chapters)

        val updatedManga = if (fetchDetails) {
            dto.series.firstOrNull()?.toSManga() ?: manga
        } else {
            manga
        }

        val updatedChapters = if (fetchChapters) {
            dto.chapters.map { it.toSChapter(slug) }
        } else {
            chapters
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.getOrNull(0) != "series") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            this.url = "/series/$slug"
        }
        return getMangaUpdate(
            manga,
            emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (slug, chapterIndex) = chapter.getSlugAndIndex(baseUrl)
        val chapterUrl = "$baseUrl/series/$slug/chapter/$chapterIndex"
        val dto = client.get(
            chapterUrl,
            rscHeaders,
            Fetch.XHR,
        ).extractNextJs<ChapterPageDto> {
            it is JsonObject && "chapter" in it
        } ?: error("Failed to extract chapter pages")

        return dto.chapter.toPageList(chapterUrl)
    }

    override fun getImageRequest(page: Page): Request {
        val referer = page.url.ifEmpty { "$baseUrl/" }
        return super.getImageRequest(page).newBuilder()
            .header("Referer", referer)
            .build()
    }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val dto = client.get("$baseUrl/browse", rscHeaders, Fetch.XHR).extractNextJs<FilterPayloadDto> {
            it is JsonObject && "genres" in it
        } ?: return JsonArray(emptyList())
        return dto.genres.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreItem>>()?.map { it.toPair() }?.toTypedArray()
            ?: DEFAULT_GENRES
        return FilterList(
            SortFilter(),
            StatusFilter(),
            FormatFilter(),
            TypeFilter(),
            GenreFilter(genres),
        )
    }

    companion object {
        private val DEFAULT_GENRES = arrayOf(
            Pair("Adult", "adult"),
            Pair("4-Koma", "4-koma"),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Cooking", "cooking"),
            Pair("Demons", "demons"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Game", "game"),
            Pair("Gender Bender", "gender bender"),
            Pair("Gore", "gore"),
            Pair("Harem", "harem"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Magic", "magic"),
            Pair("Martial Arts", "martial arts"),
            Pair("Mature", "mature"),
            Pair("Mecha", "mecha"),
            Pair("Medical", "medical"),
            Pair("Military", "military"),
            Pair("Music", "music"),
            Pair("Mystery", "mystery"),
            Pair("One-Shot", "one-shot"),
            Pair("Police", "police"),
            Pair("Psychological", "psychological"),
            Pair("Reincarnation", "reincarnation"),
            Pair("Romance", "romance"),
            Pair("School", "school"),
            Pair("School Life", "school life"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shoujo Ai", "shoujo ai"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen ai"),
            Pair("Slice of Life", "slice of life"),
            Pair("Sports", "sports"),
            Pair("Super Power", "super power"),
            Pair("Supernatural", "supernatural"),
            Pair("Thriller", "thriller"),
            Pair("Tragedy", "tragedy"),
            Pair("Vampire", "vampire"),
            Pair("Webtoons", "webtoons"),
            Pair("Yuri", "yuri"),
        )
    }
}
