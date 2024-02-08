package eu.kanade.tachiyomi.extension.all.asmhentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

open class AsmHentai(override val lang: String, private val tlTag: String) : ParsedHttpSource() {

    override val client: OkHttpClient = network.cloudflareClient

    override val baseUrl = "https://asmhentai.com"

    override val name = "AsmHentai"

    override val supportsLatest = false

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (tlTag.isNotEmpty()) addPathSegments("language/$tlTag/")
            if (page > 1) addQueryParameter("page", page.toString())
        }
        return GET(url.build(), headers)
    }

    override fun popularMangaSelector(): String = ".preview_item"

    private fun Element.mangaTitle() = select("h2").text()

    private fun Element.mangaUrl() = select(".image a").attr("abs:href")

    private fun Element.mangaThumbnail() = select(".image img").attr("abs:src")

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.mangaTitle()
            setUrlWithoutDomain(element.mangaUrl())
            thumbnail_url = element.mangaThumbnail()
        }
    }

    override fun popularMangaNextPageSelector(): String = "li.active + li:not(.disabled)"

    // Latest

    override fun latestUpdatesNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesSelector(): String {
        throw UnsupportedOperationException()
    }

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith(PREFIX_ID_SEARCH) -> {
                val id = query.removePrefix(PREFIX_ID_SEARCH)
                client.newCall(searchMangaByIdRequest(id))
                    .asObservableSuccess()
                    .map { response -> searchMangaByIdParse(response, id) }
            }
            query.toIntOrNull() != null -> {
                client.newCall(searchMangaByIdRequest(query))
                    .asObservableSuccess()
                    .map { response -> searchMangaByIdParse(response, query) }
            }
            else -> super.fetchSearchManga(page, query, filters)
        }
    }

    // any space except after a comma (we're going to replace spaces only between words)
    private val spaceRegex = Regex("""(?<!,)\s+""")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tags = (filters.last() as TagFilter).state

        val q = when {
            tags.isBlank() -> query
            query.isBlank() -> tags
            else -> "$query,$tags"
        }.replace(spaceRegex, "+")

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("search/")
            addEncodedQueryParameter("q", q)
            if (page > 1) addQueryParameter("page", page.toString())
        }

        return GET(url.build(), headers)
    }

    private class SMangaDto(
        val title: String,
        val url: String,
        val thumbnail: String,
        val lang: String,
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()

        val mangas = doc.select(searchMangaSelector())
            .map {
                SMangaDto(
                    title = it.mangaTitle(),
                    url = it.mangaUrl(),
                    thumbnail = it.mangaThumbnail(),
                    lang = it.select("a:has(.flag)").attr("href").removeSuffix("/").substringAfterLast("/"),
                )
            }
            .let { unfiltered ->
                if (tlTag.isNotEmpty()) unfiltered.filter { it.lang == tlTag } else unfiltered
            }
            .map {
                SManga.create().apply {
                    title = it.title
                    setUrlWithoutDomain(it.url)
                    thumbnail_url = it.thumbnail
                }
            }

        return MangasPage(mangas, doc.select(searchMangaNextPageSelector()).isNotEmpty())
    }

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/g/$id/", headers)

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.url = "/g/$id/"
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    private fun Element.get(tag: String): String {
        return select(".tags:contains($tag) .tag").joinToString { it.ownText() }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            document.select(".book_page").first()!!.let { element ->
                thumbnail_url = element.select(".cover img").attr("abs:src")
                title = element.select("h1").text()
                genre = element.get("Tags")
                artist = element.get("Artists")
                author = artist
                description = listOf("Parodies", "Groups", "Languages", "Category")
                    .mapNotNull { tag ->
                        element.get(tag).let { if (it.isNotEmpty()) "$tag: $it" else null }
                    }
                    .joinToString("\n", postfix = "\n") +
                    element.select(".pages h3").text() +
                    element.select("h1 + h2").text()
                        .let { altTitle -> if (altTitle.isNotEmpty()) "\nAlternate Title: $altTitle" else "" }
            }
        }
    }

    // Chapters

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(
            listOf(
                SChapter.create().apply {
                    name = "Chapter"
                    url = manga.url
                },
            ),
        )
    }

    override fun chapterListSelector(): String {
        throw UnsupportedOperationException()
    }

    override fun chapterFromElement(element: Element): SChapter {
        throw UnsupportedOperationException()
    }

    // Pages

    // convert thumbnail URLs to full image URLs
    private fun String.full(): String {
        val fType = substringAfterLast("t")
        return replace("t$fType", fType)
    }

    private fun Document.inputIdValueOf(string: String): String {
        return select("input[id=$string]").attr("value")
    }

    override fun pageListParse(document: Document): List<Page> {
        val thumbUrls = document.select(".preview_thumb img")
            .map { it.attr("abs:data-src") }
            .toMutableList()

        // input only exists if pages > 10 and have to make a request to get the other thumbnails
        val totalPages = document.inputIdValueOf("t_pages")

        if (totalPages.isNotEmpty()) {
            val token = document.select("[name=csrf-token]").attr("content")

            val form = FormBody.Builder()
                .add("_token", token)
                .add("id", document.inputIdValueOf("load_id"))
                .add("dir", document.inputIdValueOf("load_dir"))
                .add("visible_pages", "10")
                .add("t_pages", totalPages)
                .add("type", "2") // 1 would be "more", 2 is "all remaining"
                .build()

            val xhrHeaders = headers.newBuilder()
                .add("X-Requested-With", "XMLHttpRequest")
                .build()

            client.newCall(POST("$baseUrl/inc/thumbs_loader.php", xhrHeaders, form))
                .execute()
                .asJsoup()
                .select("img")
                .mapTo(thumbUrls) { it.attr("abs:data-src") }
        }
        return thumbUrls.mapIndexed { i, url -> Page(i, "", url.full()) }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Separate tags with commas (,)"),
        TagFilter(),
    )

    class TagFilter : Filter.Text("Tags")

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }
}
