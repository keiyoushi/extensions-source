package eu.kanade.tachiyomi.extension.en.onlythebesthentai

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class OnlyTheBestHentai : KeiSource() {

    private val challengeInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val peek = response.peekBody(512).string()
        if (peek.contains("One moment, please") || peek.contains("wsidchk")) {
            throw Exception(
                "Bot protection detected. Open this source in WebView to solve the challenge, " +
                    "then return to Mihon.",
            )
        }
        response
    }

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(challengeInterceptor)

    // ============================= Popular / Latest ===========================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList("$baseUrl/${if (page > 1) "page/$page/" else ""}")

    override val supportsLatest = false

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    private suspend fun parseMangaList(url: String): MangasPage {
        val doc = client.get(url).asJsoup()
        val mangas = doc.select("article.post").map(::elementToManga)
        return MangasPage(mangas, doc.selectFirst("a.next.page-numbers") != null)
    }

    private fun elementToManga(el: Element): SManga = SManga.create().apply {
        val a = el.selectFirst(".blog-entry-title a, .entry-title a")!!
        setUrlWithoutDomain(a.absUrl("href"))
        title = a.text().replace(TITLE_CLEANUP_REGEX, "").trim()
        thumbnail_url = el.selectFirst(".nv-post-thumbnail-wrap img")?.attr("abs:src")
    }

    // =============================== Search ==================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
                .apply { if (page > 1) addQueryParameter("paged", page.toString()) }
                .build()
            return parseMangaList(url.toString())
        }

        val filterUrl = filters.filterIsInstance<TaxonomyFilter>().firstNotNullOfOrNull { filter ->
            filter.selectedSlug?.let { "$baseUrl/${filter.path}/$it/" }
        } ?: "$baseUrl/"

        return parseMangaList("$filterUrl${if (page > 1) "page/$page/" else ""}")
    }

    // ======================== Manga Details / Chapters ========================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga)).asJsoup()

        val details = SManga.create().apply {
            title = doc.selectFirst("h1.manga-title")!!.text()
            thumbnail_url = doc.selectFirst(".manga-box .manga-img img")?.attr("abs:src")
            genre = doc.select(
                ".manga-tags-container:has(.manga-tags-label:containsOwn(Tags)) .tag-button",
            ).joinToString { el: Element -> el.text() }
            author = doc.select(
                ".manga-tags-container:has(.manga-tags-label:containsOwn(Artist)) .tag-button",
            ).joinToString { el: Element -> el.text() }
            description = buildDescription(doc)
            status = SManga.COMPLETED
        }

        val pageCount = doc.select(".manga-tags-container").firstNotNullOfOrNull { container: Element ->
            val label = container.selectFirst(".manga-tags-label")?.text()
                ?: return@firstNotNullOfOrNull null
            if (!label.startsWith("Pages")) return@firstNotNullOfOrNull null
            container.text().replace(NON_DIGIT_REGEX, "").toIntOrNull()
        }

        val chapter = SChapter.create().apply {
            setUrlWithoutDomain(doc.location())
            name = if (pageCount != null) "Chapter [$pageCount pages]" else "Chapter"
            chapter_number = 1f
            date_upload = Instant.tryParse(doc.selectFirst("meta[property=article:published_time]")?.attr("content"))
        }

        return SMangaUpdate(details, listOf(chapter))
    }

    private fun buildDescription(doc: Document): String = buildString {
        val parodies = doc.select(
            ".manga-tags-container:has(.manga-tags-label:containsOwn(Parody)) .tag-button",
        ).map { el: Element -> el.text() }
        if (parodies.isNotEmpty()) appendLine("Parody: ${parodies.joinToString()}")

        val characters = doc.select(
            ".manga-tags-container:has(.manga-tags-label:containsOwn(Characters)) .tag-button",
        ).map { el: Element -> el.text() }
        if (characters.isNotEmpty()) appendLine("Characters: ${characters.joinToString()}")

        val pages = doc.select(".manga-tags-container:has(.manga-tags-label:containsOwn(Pages))")
            .firstOrNull()?.text()?.replace(NON_DIGIT_REGEX, "")
        if (!pages.isNullOrEmpty()) appendLine("Pages: $pages")

        val body = doc.selectFirst(".manga-info p")
            ?.text()?.removePrefix("Description:")?.trim()
        if (!body.isNullOrEmpty()) {
            if (isNotEmpty()) appendLine()
            append(body)
        }
    }.trim()

    // ============================== Page List ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select(".manga-gallery-wrapper figure.wp-block-image img")
        .mapIndexed { i: Int, img: Element -> Page(i, imageUrl = bestImageUrl(img)) }

    private fun bestImageUrl(img: Element): String {
        val srcset = img.attr("srcset")
        if (srcset.isNotBlank()) {
            val best = srcset.split(",").map { it.trim() }
                .maxByOrNull { entry: String ->
                    entry.split(WHITESPACE_REGEX).lastOrNull()?.removeSuffix("w")?.toIntOrNull() ?: 0
                }
            val url = best?.split(WHITESPACE_REGEX)?.firstOrNull()
            if (!url.isNullOrBlank()) return url
        }
        return img.attr("abs:src")
    }

    // ============================== Filters ==================================

    @Serializable
    private class FilterEntry(val name: String, val slug: String, val count: Int) {
        override fun toString() = "$name ($count)"
    }

    @Serializable
    private class TaxonomyDto(private val name: String, private val slug: String, private val count: Int = 0) {
        fun toFilterEntry() = FilterEntry(name, slug, count)
    }

    @Serializable
    private class FilterData(
        val tags: List<FilterEntry>,
        val parodies: List<FilterEntry>,
        val characters: List<FilterEntry>,
        val artists: List<FilterEntry>,
    )

    private suspend fun fetchTaxonomy(restPath: String): List<FilterEntry> {
        val result = mutableListOf<FilterEntry>()
        var page = 1
        var totalPages = 1

        do {
            val response = client.get("$baseUrl/wp-json/wp/v2/$restPath?per_page=100&page=$page")
            if (page == 1) {
                totalPages = response.header("X-WP-TotalPages")?.toIntOrNull() ?: 1
            }
            result += response.parseAs<List<TaxonomyDto>>().map { it.toFilterEntry() }
            page++
        } while (page <= totalPages)

        return result.sortedBy { it.name.lowercase() }
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = FilterData(
        tags = fetchTaxonomy("tags"),
        parodies = fetchTaxonomy("categories"),
        characters = fetchTaxonomy("characters"),
        artists = fetchTaxonomy("artist"),
    ).toJsonElement()

    private class TaxonomyFilter(name: String, val path: String, private val entries: List<FilterEntry>) :
        Filter.Select<String>(
            name,
            (listOf("Any") + entries.map { it.toString() }).toTypedArray(),
        ) {
        val selectedSlug get() = entries.getOrNull(state - 1)?.slug
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>() ?: return FilterList()

        return FilterList(
            Filter.Header("Only one filter applies at a time (first selected wins)"),
            TaxonomyFilter("Tag", "tag", filterData.tags),
            TaxonomyFilter("Parody", "parody", filterData.parodies),
            TaxonomyFilter("Character", "characters", filterData.characters),
            TaxonomyFilter("Artist", "artist", filterData.artists),
        )
    }

    companion object {
        private val TITLE_CLEANUP_REGEX = Regex("""\s*\[\d+]\s*$""")
        private val NON_DIGIT_REGEX = Regex("[^0-9]")
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
