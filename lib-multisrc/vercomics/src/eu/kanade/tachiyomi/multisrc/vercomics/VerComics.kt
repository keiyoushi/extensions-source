package eu.kanade.tachiyomi.multisrc.vercomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class VerComics : KeiSource() {

    override val supportsLatest: Boolean = false

    protected open val urlSuffix = ""
    protected open val genreSuffix = ""
    protected open val useSuffixOnSearch = true

    // ============================== Popular ==============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$baseUrl/$urlSuffix/page/$page")
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = document.selectFirst(popularMangaNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun popularMangaSelector() = "header:has(h1) ~ * .entry"

    protected open fun popularMangaNextPageSelector() = "div.wp-pagenavi > span.current + a"

    protected open fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("a.popimg")?.let {
            setUrlWithoutDomain(it.attr("abs:href"))
            title = it.selectFirst("img")?.attr("alt") ?: ""
            thumbnail_url = it.selectFirst("img:not(noscript img)")?.imgAttr()
        }
    }

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        var url = baseUrl.toHttpUrl().newBuilder()

        if (query.isNotEmpty()) {
            url = baseUrl.toHttpUrl().newBuilder()
            if (useSuffixOnSearch) {
                url.addPathSegments(urlSuffix)
            }
            url.addPathSegments("page")
            url.addPathSegments(page.toString())
            url.addQueryParameter("s", query)

            val response = client.get(url.build())
            return searchMangaParse(response.asJsoup())
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    val uriPart = filter.toUriPart()
                    if (uriPart.isNotEmpty()) {
                        url.addPathSegments(genreSuffix)
                        url.addPathSegments(uriPart)
                        url.addPathSegments("page")
                        url.addPathSegments(page.toString())
                    }
                }
                else -> {}
            }
        }

        val response = client.get(url.build())
        return searchMangaParse(response.asJsoup())
    }

    private fun searchMangaParse(document: Document): MangasPage {
        val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }
        val hasNextPage = document.selectFirst(searchMangaNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun searchMangaSelector() = popularMangaSelector()

    protected open fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    protected open fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    // ============================== Details ==============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        var sManga = manga

        if (fetchDetails) {
            val response = client.get(baseUrl + manga.url)
            val document = response.asJsoup()

            sManga = SManga.create().apply {
                url = manga.url
                document.selectFirst("div.tax_post")?.let {
                    status = SManga.COMPLETED
                    update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                    val genreList = document.select("div.tax_box:has(div.title:contains(Etiquetas)) a[rel=tag]")
                    genre = genreList.joinToString { genre ->
                        val text = genre.text().replaceFirstChar { it.uppercase() }
                        val slug = genre.attr("href").substringAfter("$baseUrl/$genreSuffix/").removeSuffix("/")
                        val newPair = Pair(text, slug)

                        if (!genres.contains(newPair)) {
                            genres += newPair
                        }

                        text
                    }
                }
            }
        }

        val sChapters = listOf(
            SChapter.create().apply {
                name = manga.title
                url = manga.url
            },
        )

        return SMangaUpdate(sManga, sChapters)
    }

    // =============================== Pages ===============================
    protected open val pageListSelector =
        "div.wp-content p > img:not(noscript img), " +
            "div.wp-content div#lector > img:not(noscript img), " +
            "div.wp-content > figure img:not(noscript img), " +
            "div.wp-content > img, div.wp-content > p img, " +
            "div.post-imgs > img"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(baseUrl + chapter.url)
        val document = response.asJsoup()
        return document.select(pageListSelector).mapIndexed { i, img ->
            Page(i, imageUrl = img.imgAttr() ?: img.attr("abs:src"))
        }
    }

    protected open fun Element.imgAttr(): String? = when {
        this.hasAttr("data-src") -> this.attr("abs:data-src")
        this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
        this.hasAttr("srcset") -> this.attr("abs:srcset").getSrcSetImage()
        this.hasAttr("data-cfsrc") -> this.attr("abs:data-cfsrc")
        else -> this.attr("abs:src")
    }

    private fun String.getSrcSetImage(): String? = this.split(" ")
        .filter { URL_REGEX.matches(it) }
        .maxOfOrNull { it }

    // ============================== Filters ==============================
    protected open var genres = arrayOf(Pair("Ver todos", ""))

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = listOf(
            Filter.Header("Los filtros serán ignorados si la búsqueda no está vacía."),
            Filter.Separator(),
            GenreFilter(genres),
        )

        return FilterList(filters)
    }

    open class UriPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
    ) : Filter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
    ) {
        fun toUriPart() = vals[state].second
    }

    class GenreFilter(genres: Array<Pair<String, String>>) : UriPartFilter("Filtrar por género", genres)

    companion object {
        private val URL_REGEX = """^(https?://[^\s/$.?#].\S*)$""".toRegex()
    }
}
