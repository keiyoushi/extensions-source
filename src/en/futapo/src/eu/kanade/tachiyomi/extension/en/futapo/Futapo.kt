package eu.kanade.tachiyomi.extension.en.futapo

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.parser.Parser
import java.io.IOException
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Source
abstract class Futapo : KeiSource() {

    // The site has no popularity ranking; comics newest first, like its comics index
    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(botCheckInterceptor)

    // LiteSpeed's reCAPTCHA, in front of the API and uncached pages while the site is busy
    private val botCheckInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (response.code == 403 && BOT_CHECK_FORM in response.peekBody(8192).string()) {
            response.close()
            throw IOException("Open in WebView from Browse to pass the site's bot check")
        }
        response
    }

    // The site's search page is never served from Cloudflare's cache, so WebView shows the bot check when it's on
    override fun getHomeUrl() = "$baseUrl/?s="

    override suspend fun getPopularManga(page: Int): MangasPage = fetchEntries("pages", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val tagId = filters.firstInstanceOrNull<TagFilter>()?.id
        val endpoint = if (tagId != null) "posts" else filters.firstInstanceOrNull<TypeFilter>()?.endpoint
        if (endpoint == null) return searchAll(page, query)
        return fetchEntries(endpoint, page) {
            if (query.isNotBlank()) addQueryParameter("search", query)
            if (tagId != null) addQueryParameter("tags", tagId.toString())
        }
    }

    // Comics are WordPress pages, blog galleries are posts
    private suspend fun fetchEntries(endpoint: String, page: Int, params: HttpUrl.Builder.() -> Unit = {}): MangasPage {
        val url = "$baseUrl/wp-json/wp/v2/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("per_page", PER_PAGE)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("_fields", "link,title,_links,_embedded")
            .addQueryParameter("_embed", "wp:featuredmedia")
            .apply(params)
            .build()
        val response = client.get(url, Fetch.XHR)
        val hasNextPage = page < response.totalPages
        val mangas = response.parseAs<List<EntryDto>>().map { entry ->
            SManga.create().apply {
                setUrlWithoutDomain(entry.link)
                title = Parser.unescapeEntities(entry.title.rendered, false)
                thumbnail_url = entry.cover
            }
        }
        return MangasPage(mangas, hasNextPage)
    }

    // Comics and blog posts in one list, like the site's own search
    private suspend fun searchAll(page: Int, query: String): MangasPage {
        val url = "$baseUrl/wp-json/wp/v2/search".toHttpUrl().newBuilder()
            .addQueryParameter("type", "post")
            .addQueryParameter("subtype", "page,post")
            .addQueryParameter("per_page", PER_PAGE)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("_fields", "id,title,url,subtype")
            .apply { if (query.isNotBlank()) addQueryParameter("search", query) }
            .build()
        val response = client.get(url, Fetch.XHR)
        val hasNextPage = page < response.totalPages
        val results = response.parseAs<List<SearchResultDto>>()
        // Search results carry no cover; one request per type fetches them
        val covers = coroutineScope {
            results.groupBy { it.subtype }.map { (subtype, entries) ->
                async { fetchCovers(SUBTYPE_ENDPOINTS[subtype]!!, entries.map { it.id }) }
            }.awaitAll()
        }.flatten().associate { it.id to it.cover }
        val mangas = results.map { result ->
            SManga.create().apply {
                setUrlWithoutDomain(result.url)
                title = Parser.unescapeEntities(result.title, false)
                thumbnail_url = covers[result.id]
            }
        }
        return MangasPage(mangas, hasNextPage)
    }

    private suspend fun fetchCovers(endpoint: String, ids: List<Int>): List<CoverDto> {
        val url = "$baseUrl/wp-json/wp/v2/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("include", ids.joinToString(","))
            .addQueryParameter("per_page", ids.size.toString())
            .addQueryParameter("_fields", "id,_links,_embedded")
            .addQueryParameter("_embed", "wp:featuredmedia")
            .build()
        return client.get(url, Fetch.XHR).parseAs()
    }

    private val Response.totalPages get() = header("X-WP-TotalPages")!!.toInt()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        // Older posts link to each other as /yyyy/mm/dd/<slug>/
        val slug = segments.singleOrNull()
            ?: segments.takeIf { it.size == 4 && it.dropLast(1).all { part -> part.toIntOrNull() != null } }?.last()
            ?: return null
        val entryUrl = baseUrl.toHttpUrl().newBuilder().addPathSegment(slug).addPathSegment("").build()
        val document = client.get(entryUrl, Fetch.DOCUMENT).asJsoup()
        if (!document.body().hasClass("single-post") && !document.body().hasClass("page")) return null
        return SManga.create().apply {
            setUrlWithoutDomain(document.selectFirst("link[rel=canonical]")!!.absUrl("href"))
            title = document.selectFirst("h1.entry-title")!!.text()
            thumbnail_url = document.selectFirst(PAGE_SELECTOR)?.absUrl("href")
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val restUrl = document.selectFirst("link[rel=alternate][type=application/json]")!!
            .absUrl("href").toHttpUrl().newBuilder()
            .addQueryParameter("_fields", "date_gmt,title,_links,_embedded")
            .addQueryParameter("_embed", "wp:featuredmedia")
            .build()
        val entry = client.get(restUrl, Fetch.XHR).parseAs<DetailsDto>()

        val title = Parser.unescapeEntities(entry.title.rendered, false)
        val (artistTags, tags) = document.select("footer.entry-meta a[rel=tag]").map { it.text() }
            .partition { it.startsWith(ARTIST_TAG) }
        val isComic = document.body().hasClass("page")
        manga.apply {
            this.title = title
            if (isComic) {
                artist = ARTIST_REGEX.matchEntire(title)?.groupValues?.get(1)?.split(" and ")?.joinToString()
                // Parody and section labels the site puts in front of comic titles
                genre = TITLE_LABELS_REGEX.find(title)?.value
                    ?.let { labels -> TITLE_LABEL_REGEX.findAll(labels).map { it.groupValues[1] }.joinToString() }
            } else {
                artist = artistTags.map { it.removePrefix(ARTIST_TAG) }.takeIf { it.isNotEmpty() }?.joinToString()
                genre = tags.takeIf { it.isNotEmpty() }?.joinToString()
            }
            author = artist
            description = MarkdownDescription.Builder().apply {
                document.select(".entry-content > p:not(:has(img))").forEach(::description)
            }.build()
            // Some older entries have no featured image; their first page stands in, as on the site's album pages
            (entry.cover ?: document.selectFirst(PAGE_SELECTOR)?.absUrl("href"))?.let { thumbnail_url = it }
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Chapter"
            date_upload = DateTimeFormatter.ISO_LOCAL_DATE_TIME.tryParseDateTime(entry.dateGmt, ZoneOffset.UTC)
        }
        return SMangaUpdate(manga, listOf(chapter))
    }

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select(".yarpp-related a.yarpp-thumbnail")
        .map { link ->
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.selectFirst(".yarpp-thumbnail-title")!!.text()
                thumbnail_url = link.selectFirst("noscript > img")?.absUrl("src")
            }
        }

    // Full-size links of the comic's gallery or of the post's images; video stills aren't linked
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(PAGE_SELECTOR)
        .mapIndexed { index, link -> Page(index, imageUrl = link.absUrl("href")) }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val firstPage = client.get(tagsUrl(1), Fetch.XHR)
        val totalPages = firstPage.totalPages
        val tags = coroutineScope {
            val otherPages = (2..totalPages).map { page ->
                async { client.get(tagsUrl(page), Fetch.XHR).parseAs<List<TagDto>>() }
            }
            firstPage.parseAs<List<TagDto>>() + otherPages.awaitAll().flatten()
        }
        return tags.toJsonElement()
    }

    private fun tagsUrl(page: Int) = "$baseUrl/wp-json/wp/v2/tags".toHttpUrl().newBuilder()
        .addQueryParameter("per_page", "100")
        .addQueryParameter("page", page.toString())
        .addQueryParameter("hide_empty", "true")
        .addQueryParameter("orderby", "name")
        .addQueryParameter("_fields", "id,name")
        .build()

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = data?.parseAs<List<TagDto>>() ?: return FilterList(TypeFilter())
        return FilterList(TypeFilter(), TagFilter(tags))
    }

    companion object {
        private const val PER_PAGE = "24"
        private const val BOT_CHECK_FORM = "lsrecaptcha-form"
        private const val PAGE_SELECTOR = ".entry-content a[href*=/wp-content/]:has(img)"
        private const val ARTIST_TAG = "(artist) "
        private val SUBTYPE_ENDPOINTS = mapOf("page" to "pages", "post" to "posts")
        private val ARTIST_REGEX = Regex(""".* by (.+)""", RegexOption.IGNORE_CASE)
        private val TITLE_LABELS_REGEX = Regex("""^(?:\[[^\]]+])+""")
        private val TITLE_LABEL_REGEX = Regex("""\[([^\]]+)]""")
    }
}
