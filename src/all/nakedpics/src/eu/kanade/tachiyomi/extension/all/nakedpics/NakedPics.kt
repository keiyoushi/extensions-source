package eu.kanade.tachiyomi.extension.all.nakedpics

import eu.kanade.tachiyomi.network.HttpException
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
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class NakedPics : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3) { it.host == baseUrl.toHttpUrl().host }

    override suspend fun getPopularManga(page: Int) = getListing(siteUrl("popular"), page)

    override suspend fun getLatestUpdates(page: Int) = getListing(siteUrl("recent"), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val newest = filters.firstInstance<SortFilter>().newest
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.selected
        return when {
            // The site's search form lands on /search/<words joined by "-">/
            query.isNotBlank() -> getListing(siteUrl("search", query.trim().split(WHITESPACE).joinToString("-")), page, newest)
            category != null -> getListing(if (newest) siteUrl(category, "newest") else siteUrl(category), page)
            newest -> getLatestUpdates(page)
            else -> getPopularManga(page)
        }
    }

    private fun siteUrl(vararg segments: String): HttpUrl = baseUrl.toHttpUrl().newBuilder().apply {
        segments.forEach(::addPathSegment)
        addPathSegment("")
    }.build()

    private class Listing(val url: HttpUrl, val pageUrl: HttpUrl, val cid: String?)

    // Last listing page 1 resolved: searches redirect to a model, channel, tag or category page
    private var listing: Listing? = null

    // newest: switch a resolved search to its newest/ list, which the search redirect drops
    private suspend fun getListing(url: HttpUrl, page: Int, newest: Boolean = false): MangasPage {
        val resolved = listing?.takeIf { page > 1 && it.url == url } ?: run {
            val document = try {
                client.get(url, Fetch.DOCUMENT).asJsoup()
            } catch (e: HttpException) {
                // Searches without results answer 404 with the popular galleries
                if (e.code != 404 || url.pathSegments[0] != "search") throw e
                return MangasPage(emptyList(), false)
            }
            val cid = document.select("script").firstNotNullOfOrNull { CID_REGEX.find(it.data())?.groupValues?.get(1) }
            val fresh = Listing(url, document.location().toHttpUrl(), cid)
            listing = fresh
            if (page == 1 && !newest) {
                val cards = document.select("[itemprop=associatedMedia] > a[href*=/gals/]")
                // A gallery can have several cards
                val mangas = cards.map { link ->
                    val image = link.selectFirst("img")!!
                    SManga.create().apply {
                        this.url = link.absUrl("href").toHttpUrl().encodedPath
                        title = image.attrOrNull("alt")!!.removeSuffix(" " + link.attr("data-tid").substringBefore('_'))
                        thumbnail_url = image.attrOrNull("abs:data-src") ?: image.attrOrNull("abs:src")
                    }
                }
                return MangasPage(mangas.distinctBy { it.url }, cards.size >= PAGE_SIZE)
            }
            fresh
        }
        val pageUrl = resolved.pageUrl.newBuilder().apply {
            if (newest) addPathSegment("newest").addPathSegment("")
            // The page script holds CID form-encoded ("sunny+leone")
            addQueryParameter("cat", checkNotNull(resolved.cid) { "No CID on ${resolved.pageUrl}" }.replace('+', ' '))
            addQueryParameter("limit", PAGE_SIZE.toString())
            addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
            // Language of the titles the site returns
            addQueryParameter("lang", "en")
        }.build()
        val galleries = client.get(pageUrl, Fetch.XHR).parseAs<List<GalleryDto>>()
        val mangas = galleries.mapNotNull { it.toSManga(baseUrl.toHttpUrl()) }.distinctBy { it.url }
        return MangasPage(mangas, galleries.size >= PAGE_SIZE)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        val segments = url.siteSegments()
        val newest = segments.size > 1 && segments.last() == "newest"
        val size = if (newest) segments.size - 1 else segments.size
        return when {
            // The search redirect drops newest/
            size == 2 && segments[0] == "search" -> getListing(siteUrl("search", segments[1]), page, newest)
            (size == 2 && segments[0] in LIST_TYPES) || (size == 1 && segments[0] !in INDEX_PAGES) -> getListing(siteUrl(*segments.toTypedArray()), page)
            else -> super.getMangasByUrl(url, page)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.siteSegments()
        if (url.host != baseUrl.toHttpUrl().host || segments.size != 2 || segments[0] != "gals") return null
        val manga = SManga.create().apply { this.url = "/gals/${segments[1]}/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // Path segments without the site's language prefix (/de/, /jp/...)
    private fun HttpUrl.siteSegments() = pathSegments.filter { it.isNotEmpty() }.let { segments ->
        if (segments.size > 1 && LANGUAGE_SEGMENT.matches(segments[0])) segments.drop(1) else segments
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        // The tag list marks models, channels and tags with icons
        val models = document.select("$TAG_LINK:has(> i.icon-mod)").map { it.text() to it.absUrl("href") }
        val channel = document.selectFirst("$TAG_LINK:has(> i.icon-cat2)")
        val photos = document.select(PAGE_SELECTOR)

        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("h1[itemprop=name]")!!.text()
            author = models.joinToString { it.first }.ifEmpty { null }
            artist = channel?.text()
            genre = document.select("$TAG_LINK:has(> i.icon-tag2-1)").joinToString { it.text() }.ifEmpty { null }
            description = MarkdownDescription.Builder()
                .info("${photos.size} photos")
                .links(models, "Models")
                .links(listOfNotNull(channel?.let { it.text() to it.absUrl("href") }), "Channel")
                .build()
            // Photo thumbs are lazy: src is a placeholder, itemprop holds the image url
            thumbnail_url = manga.thumbnail_url ?: photos.firstOrNull()?.selectFirst("img")?.attrOrNull("abs:itemprop")
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Gallery"
        }
        return SMangaUpdate(details, listOf(chapter))
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val mangaUrl = getMangaUrl(manga)
        val galleryId = mangaUrl.toHttpUrl().pathSegments[1].substringAfterLast('-')
        val url = siteUrl("related", "id", galleryId).newBuilder().addQueryParameter("lang", "en").build()
        return client.get(url, Fetch.XHR.from(mangaUrl)).parseAs<List<GalleryDto>>()
            .mapNotNull { it.toSManga(baseUrl.toHttpUrl()) }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(PAGE_SELECTOR).mapIndexed { index, link -> Page(index, imageUrl = link.absUrl("href")) }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val categories = client.get(baseUrl, Fetch.DOCUMENT).asJsoup().select("[itemprop=associatedMedia] > a:has(> span)").map { link ->
            CategoryOption(link.selectFirst("> span")!!.text(), link.absUrl("href").toHttpUrl().pathSegments[0])
        }
        return FilterData(categories).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOfNotNull(
            SortFilter(),
            data?.parseAs<FilterData>()?.let { CategoryFilter(it.categories) },
        ),
    )

    companion object {
        private const val PAGE_SIZE = 30
        private const val PAGE_SELECTOR = "[itemprop=associatedMedia] > a[data-pswp-width]"
        private const val TAG_LINK = "[itemtype$=ImageGallery] li > a"
        private val LIST_TYPES = setOf("models", "channels", "tags")
        private val INDEX_PAGES = LIST_TYPES + setOf("gals", "search", "related")
        private val CID_REGEX = Regex("""\bCID\s*=\s*["']([^"']*)""")
        private val LANGUAGE_SEGMENT = Regex("""[a-z]{2}""")
        private val WHITESPACE = Regex("""\s+""")
    }
}
