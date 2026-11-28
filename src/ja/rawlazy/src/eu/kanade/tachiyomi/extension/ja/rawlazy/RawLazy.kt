package eu.kanade.tachiyomi.extension.ja.rawlazy

import eu.kanade.tachiyomi.multisrc.zing.GenreFilter
import eu.kanade.tachiyomi.multisrc.zing.ZingBase
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class RawLazy : ZingBase() {
    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = ajax("_action" to "load_top")
        return MangasPage(result.html.asFragment().select(".swiper-slide .entry").map(::mangaFromCard), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = listPage(ALL_CATEGORIES, page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            val url = baseUrl.toHttpUrl().newBuilder().addQueryParameter("s_manga", query).build()
            val document = client.get(url, Fetch.DOCUMENT).asJsoup()
            // The site shows up to 20 results, with no further pages
            return MangasPage(document.select(".row-of-mangas .entry-tag").map(::mangaFromCard), false)
        }
        val category = filters.firstInstanceOrNull<GenreFilter>()?.value ?: ALL_CATEGORIES
        return listPage(category, page)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (!isSiteHost(url) || segments.size != 2 || segments[0] != GENRE_PATH) return super.getMangasByUrl(url, page)
        val categoryUrl = baseUrl.toHttpUrl().newBuilder()
            .encodedPath(CATEGORIES_PATH)
            .addQueryParameter("slug", segments[1])
            .addQueryParameter("_fields", "id,name")
            .build()
        val category = client.get(categoryUrl, Fetch.XHR).parseAs<List<CategoryDto>>().firstOrNull()
            ?: return MangasPage(emptyList(), false)
        return listPage(category.id.toString(), page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!isSiteHost(url)) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val mangaUrl = when {
            segments.size == 2 && segments[0] == MANGA_PATH -> baseUrl.toHttpUrl().newBuilder()
                .addPathSegment(MANGA_PATH)
                .addPathSegment(segments[1])
                .addPathSegment("")
                .build()
            // A chapter page links its series
            segments.size == 1 -> {
                val chapterUrl = baseUrl.toHttpUrl().newBuilder().addPathSegment(segments[0]).addPathSegment("").build()
                val chapter = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
                chapter.selectFirst(".manga-name a")?.absUrl("href")?.toHttpUrl() ?: return null
            }
            else -> return null
        }
        return fetchSeries(mangaUrl)
    }

    override fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1:not(.blog-name)")!!.text().removeTitleSuffix()
        thumbnail_url = document.selectFirst("img.thumb")?.absUrl("src")
        genre = document.select(".genres-wrap a").joinToString { it.text() }.ifEmpty { null }
        description = MarkdownDescription.Builder()
            .field("Views", document.selectFirst("div:has(> strong:containsOwn(Views:))")?.ownText())
            .description(document.selectFirst(".content-text"))
            .build()
    }

    override fun parseChapters(document: Document) = document.select(".chapters-list a").map { link ->
        SChapter.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            // The newest chapter's link also holds a "new" badge
            name = link.selectFirst("span")!!.text()
        }
    }
        // The site lists some chapters twice
        .distinctBy { it.url }

    override fun readerFields(document: Document) = postIdReaderFields(document)

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val categories = buildList {
            var page = 1
            do {
                val url = baseUrl.toHttpUrl().newBuilder()
                    .encodedPath(CATEGORIES_PATH)
                    .addQueryParameter("per_page", "100")
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("_fields", "id,name")
                    .build()
                val response = client.get(url, Fetch.XHR)
                val pages = response.header("X-WP-TotalPages")!!.toInt()
                addAll(response.parseAs<List<CategoryDto>>())
            } while (page++ < pages)
        }
        return categories.map { it.name to it.id.toString() }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Pair<String, String>>>()?.takeIf { it.isNotEmpty() } ?: return FilterList()
        return FilterList(
            Filter.Header("キーワード検索ではジャンルは無視されます"),
            GenreFilter(genres),
        )
    }

    private suspend fun listPage(category: String, page: Int): MangasPage {
        val result = ajax("_action" to "loadmore_tr", "p" to page.toString(), "category_id" to category)
        val mangas = result.html.asFragment().select(".entry-tag").map(::mangaFromCard)
        return MangasPage(mangas, result.going == "yes")
    }

    // The site's ajax nonce, the same on every page until it expires
    @Volatile
    private var nonce: String? = null

    private val nonceMutex = Mutex()

    private suspend fun ajax(vararg fields: Pair<String, String>): AjaxDto {
        val current = nonce ?: freshNonce(null)
        try {
            return client.post(ajaxUrl, Fetch.XHR, ajaxBody(current, fields)).parseAs()
        } catch (e: HttpException) {
            // An expired nonce
            if (e.code != 403) throw e
        }
        return client.post(ajaxUrl, Fetch.XHR, ajaxBody(freshNonce(current), fields)).parseAs()
    }

    private suspend fun freshNonce(stale: String?): String = nonceMutex.withLock {
        nonce?.takeIf { it != stale }
            ?: client.get(baseUrl, Fetch.DOCUMENT).asJsoup().zingConfig()!!.nonce.also { nonce = it }
    }

    private fun ajaxBody(nonce: String, fields: Array<out Pair<String, String>>) = FormBody.Builder()
        .add("action", "z_do_ajax")
        .apply { fields.forEach { (name, value) -> add(name, value) } }
        .add("nonce", nonce)
        .build()

    private fun String.asFragment() = Jsoup.parseBodyFragment(this, baseUrl)

    private fun mangaFromCard(element: Element) = SManga.create().apply {
        val link = element.selectFirst("h2.name a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text().removeTitleSuffix()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        genre = element.select("h3 a").joinToString { it.text() }.ifEmpty { null }
    }

    companion object {
        private const val MANGA_PATH = "manga-lazy"
        private const val GENRE_PATH = "genres"
        private const val CATEGORIES_PATH = "/wp-json/wp/v2/categories"
        private const val ALL_CATEGORIES = "0"
    }
}
