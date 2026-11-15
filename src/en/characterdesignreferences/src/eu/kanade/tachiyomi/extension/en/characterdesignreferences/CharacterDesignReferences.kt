package eu.kanade.tachiyomi.extension.en.characterdesignreferences

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

@Source
abstract class CharacterDesignReferences : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseUrl.toHttpUrl().host }

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = getSectionListing(Section.ART_OF_ANIMATION, page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Collection numbers can be password-protected or empty, so a page is the next collection with posts
    private var collectionCursor = 1

    // Results outside the gallery sections are dropped, so a page is the next results page with any left
    private var searchCursor = 0

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getSectionListing(filters.firstInstance<SectionFilter>().selected, page)

        if (page == 1) searchCursor = 0
        while (true) {
            val searchUrl = baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("api/search/GeneralSearch")
                .addQueryParameter("q", query.trim())
                .addQueryParameter("p", searchCursor.toString())
                .addQueryParameter("size", SEARCH_SIZE.toString())
                .build()
            val response = client.get(searchUrl, Fetch.XHR.from("/search")).parseAs<SearchResponse>()
            searchCursor++
            val mangas = response.items
                .filter { isPostPath(baseUrl.toHttpUrl().resolve(it.itemUrl)!!) }
                .map { it.toSManga() }
            val hasNextPage = searchCursor * SEARCH_SIZE < response.totalCount
            if (mangas.isNotEmpty() || !hasNextPage) return MangasPage(mangas, hasNextPage)
        }
    }

    private suspend fun getSectionListing(section: Section, page: Int): MangasPage {
        if (page == 1) collectionCursor = 1
        while (true) {
            val collection = section.collection(collectionCursor++) ?: return MangasPage(emptyList(), false)
            val collectionUrl = baseUrl.toHttpUrl().newBuilder().addPathSegment(collection).build()
            var document = try {
                client.get(collectionUrl, Fetch.DOCUMENT).asJsoup()
            } catch (e: HttpException) {
                when (e.code) {
                    404 -> return MangasPage(emptyList(), false)
                    // Password-protected collection
                    401 -> continue
                    else -> throw e
                }
            }
            // A collection lists 20 posts per page and holds up to 30
            val mangas = buildList {
                while (true) {
                    document.select("article.hentry").mapTo(this, ::postCard)
                    val next = document.selectFirst("a#nextLink")?.absUrl("href") ?: break
                    document = client.get(next, Fetch.DOCUMENT).asJsoup()
                }
            }
            if (mangas.isNotEmpty()) return MangasPage(mangas, section.isNumbered)
        }
    }

    private fun postCard(article: Element) = SManga.create().apply {
        val link = article.selectFirst("h1.entry-title a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text()
        thumbnail_url = article.selectFirst(".excerpt-thumb img")?.attrOrNull("data-image")?.let(::coverUrl)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        if (!isPostPath(url)) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val manga = SManga.create().apply { this.url = "/${segments[0]}/${segments[1]}" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // Search also returns the collections themselves and posts of the video and review sections
    private fun isPostPath(url: HttpUrl): Boolean {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        return segments.size == 2 && Section.of(segments[0]) != null
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = getMangaUrl(manga)
        val item = getItem(mangaUrl)
        val section = Section.of(mangaUrl.toHttpUrl().pathSegments.first())!!
        val body = Jsoup.parseBodyFragment(item.body, baseUrl)
        // First run of text blocks: credits and synopsis, artist details, interview opening, winners
        val intro = body.select(".sqs-block-html, .sqs-block-gallery")
            .dropWhile { !it.hasClass("sqs-block-html") }
            .takeWhile { it.hasClass("sqs-block-html") }
        val details = SManga.create().apply {
            url = manga.url
            title = item.title.unescapeTitle()
            author = when {
                section.titleIsArtist -> title
                // The first gallery captions the winners
                section == Section.WINNERS -> body.selectFirst(GALLERY)?.select(".image-slide-title").orEmpty()
                    .map { it.captionText() }.distinct().joinToString().ifEmpty { null }
                else -> null
            }
            genre = (listOf(section.title) + item.categories + item.tags).distinct().joinToString()
            description = MarkdownDescription.Builder()
                .info("${body.imageUrls().size} images")
                .apply { intro.forEach { description(it) } }
                .altTitles(body.select("$GALLERY .image-slide-title").map { it.captionText() }, "Artists")
                .build()
            thumbnail_url = item.assetUrl?.let(::coverUrl) ?: manga.thumbnail_url
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Gallery"
            date_upload = item.publishOn
        }
        return SMangaUpdate(details, listOf(chapter))
    }

    // Captions carry left-to-right marks
    private fun Element.captionText() = text().replace("\u200E", "").trim()

    override suspend fun getPageList(chapter: SChapter): List<Page> = Jsoup.parseBodyFragment(getItem(getChapterUrl(chapter)).body, baseUrl)
        .imageUrls()
        .mapIndexed { index, url -> Page(index, imageUrl = url) }

    // Gallery images and the single images that aren't linked banners, in page order.
    // Slideshows repeat a grid's images (winners) or are promos
    private fun Element.imageUrls(): List<String> = select("$GALLERY img[data-image], $SINGLE_IMAGE")
        .filter { it.closest(".sqs-block-gallery") != null || it.closest(".sqs-block")!!.selectFirst("a[href]") == null }
        .map { it.absUrl("data-image") }

    private suspend fun getItem(url: String): Item {
        val jsonUrl = url.toHttpUrl().newBuilder().addQueryParameter("format", "json").build()
        return client.get(jsonUrl, Fetch.XHR.from(url)).parseAs<ItemResponse>().item
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Text search covers every section"),
        SectionFilter(),
    )

    companion object {
        private const val SEARCH_SIZE = 200
        private const val GALLERY = ".sqs-block-gallery:not(:has(.sqs-gallery-block-slideshow))"
        private const val SINGLE_IMAGE = "[data-definition-name='website.components.imageLayout'] img[data-image]"
    }
}
