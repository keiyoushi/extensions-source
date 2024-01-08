package eu.kanade.tachiyomi.extension.ru.unicomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.concurrent.TimeUnit

class UniComics : ParsedHttpSource() {

    override val name = "UniComics"

    private val baseDefaultUrl = "https://unicomics.ru"
    override var baseUrl = baseDefaultUrl

    override val lang = "ru"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36 Edg/100.0.1185.50")
        .add("Referer", baseDefaultUrl)

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseDefaultUrl/comics/series/page/$page", headers)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseDefaultUrl/comics/online/page/$page", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is GetEventsList -> {
                    if (filter.state > 0) {
                        return GET("$baseDefaultUrl$PATH_events", headers)
                    }
                }
                is Publishers -> {
                    if (filter.state > 0) {
                        val publisherName = getPublishersList()[filter.state].url
                        val publisherUrl =
                            "$baseDefaultUrl$PATH_publishers/$publisherName/page/$page".toHttpUrlOrNull()!!
                                .newBuilder()
                        return GET(publisherUrl.toString(), headers)
                    }
                }
                else -> {}
            }
        }
        if (query.isNotEmpty()) {
            return GET(
                "https://yandex.ru/search/site/?frame=1&lr=172&searchid=1959358&topdoc=xdm_e=$baseDefaultUrl&xdm_c=default5044&xdm_p=1&v=2.0&web=0&text=$query&p=$page",
                headers,
            )
        }
        return popularMangaRequest(page)
    }
    override fun searchMangaSelector() =
        ".b-serp-item__content:has(.b-serp-url__item:contains(/comics/):not(:contains($PATH_events)):not(:contains($PATH_publishers)):not(:contains(/page/))):has(.b-serp-item__title-link:not(:contains(Комиксы читать онлайн бесплатно)))"

    override fun searchMangaNextPageSelector() = ".b-pager__next"
    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("a.b-serp-item__title-link").first()!!.let {
                val originUrl = it.attr("href")
                val urlString =
                    "/characters$|/creators$".toRegex().replace(
                        "/page$".toRegex().replace(
                            "/[0-9]+/?$".toRegex().replace(
                                originUrl.replace(PATH_online, PATH_URL).replace(PATH_issue, PATH_URL),
                                "",
                            ),
                            "",
                        ),
                        "",
                    )
                val issueNumber = "-[0-9]+/?$".toRegex()
                setUrlWithoutDomain(
                    if (issueNumber.containsMatchIn(urlString) && (originUrl.contains(PATH_online) || originUrl.contains(PATH_issue))) {
                        issueNumber.replace(urlString, "")
                    } else {
                        urlString
                    },
                )

                title = it.text().substringBefore(" (").substringBefore(" №")
            }
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        if (document.location().contains("$baseDefaultUrl$PATH_events")) {
            val mangas = document.select(".list_events").map { element ->
                SManga.create().apply {
                    element.select("a").first()!!.let {
                        setUrlWithoutDomain("/" + it.attr("href"))
                        title = it.text()
                    }
                }
            }
            return MangasPage(mangas, false)
        }

        if (document.location().contains("$baseDefaultUrl/comics")) {
            val mangas = document.select(popularMangaSelector()).map { element ->
                popularMangaFromElement(element)
            }
            return MangasPage(mangas, mangas.isNotEmpty())
        }

        if (document.select(".CheckboxCaptcha").isNotEmpty()) {
            baseUrl = document.location()
            throw Exception("Пройдите капчу в WebView(слишком много запросов)")
        } else if (baseUrl != baseDefaultUrl) {
            baseUrl = baseDefaultUrl
        }

        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }
        var hasNextPage = false
        val nextSearchPage = document.select(searchMangaNextPageSelector())
        if (nextSearchPage.isNotEmpty()) {
            hasNextPage = true
        }
        return MangasPage(mangas.distinctBy { it.url }, hasNextPage)
    }

    private fun searchMangaByIdRequest(id: String): Request {
        return GET("$baseDefaultUrl$PATH_URL$id", headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SLUG_SEARCH)) {
            val realQuery = query.removePrefix(PREFIX_SLUG_SEARCH)
            client.newCall(searchMangaByIdRequest(realQuery))
                .asObservableSuccess()
                .map { response ->
                    val details = mangaDetailsParse(response)
                    details.url = PATH_URL + realQuery
                    MangasPage(listOf(details), false)
                }
        } else {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response)
                }
        }
    }

    override fun popularMangaSelector() = "div.list_comics"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select(".left_comics img").first()!!.attr("src").replace(".jpg", "_big.jpg")
        element.select("a").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
        }
        manga.title = element.select(".list_title").first()!!.text()
        return manga
    }
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseDefaultUrl + manga.url, headers)
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.select(".left_container div").first()!!
        title = infoElement.select("h1").first()!!.text()
        thumbnail_url = if (infoElement.select("img").isNotEmpty()) {
            infoElement.select("img").first()!!.attr("src")
        } else {
            document.select(".left_comics img").first()!!.attr("src").replace(".jpg", "_big.jpg")
        }
        description = infoElement.select("H2").first()!!.text() + "\n" + infoElement.select("p").last()?.text().orEmpty()
        author = infoElement.select("tr:contains(Издательство)").text()
        genre = infoElement.select("tr:contains(Жанр) a").joinToString { it.text() }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val document = client.newCall(GET(baseDefaultUrl + manga.url, headers)).execute().asJsoup()
        val pages = mutableListOf(1)
        val dataStrArray = document.toString()
            .substringAfter("new Paginator(")
            .substringBefore(");</script>")
            .split(", ")
        if (dataStrArray[1].toInt() > 1) {
            pages += (2..dataStrArray[1].toInt()).toList()
        }
        return Observable.just(
            pages.flatMap { page ->
                chapterListParse(client.newCall(chapterPageListRequest(manga, page)).execute(), manga)
            }.reversed(),
        )
    }

    private fun chapterPageListRequest(manga: SManga, page: Int): Request {
        return GET("$baseDefaultUrl${manga.url}/page/$page", headers)
    }

    override fun chapterListSelector() = "div.right_comics:has(td:contains(Читать))"

    private fun chapterListParse(response: Response, manga: SManga): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { chapterFromElement(it, manga) }
    }

    private fun chapterFromElement(element: Element, manga: SManga): SChapter {
        val urlElement = element.select("td:contains(Читать) a").first()!!
        val chapter = SChapter.create()
        element.select(".list_title").first()!!.text().let {
            val titleNoPrefix = it.removePrefix(manga.title).removePrefix(":").trim()
            chapter.name = if (titleNoPrefix.isNotEmpty()) {
                titleNoPrefix.replaceFirst(titleNoPrefix.first(), titleNoPrefix.first().uppercaseChar())
            } else {
                "Сингл"
            }
            if (titleNoPrefix.contains("№")) {
                chapter.chapter_number = titleNoPrefix.substringAfterLast("№").toFloatOrNull() ?: -1f
            }
        }
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        return chapter
    }

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(baseDefaultUrl + chapter.url, headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        val dataStrArray = document.toString()
            .substringAfter("new Paginator(")
            .substringBefore(");</script>")
            .split(", ")
        return (1..dataStrArray[1].toInt()).mapIndexed { i, page ->
            Page(i, document.location() + "/$page")
        }
    }
    override fun imageUrlParse(document: Document): String {
        return document.select(".image_online").attr("src")
    }
    private class Publishers(publishers: Array<String>) : Filter.Select<String>("Издательства (только)", publishers)

    override fun getFilterList() = FilterList(
        Publishers(publishersName),
        GetEventsList(),
    )
    private class GetEventsList : Filter.Select<String>(
        "События (только)",
        arrayOf("Нет", "в комиксах"),
    )

    private data class Publisher(val name: String, val url: String)

    private fun getPublishersList() = listOf(
        Publisher("Все", "not"),
        Publisher("Marvel", "marvel"),
        Publisher("DC Comics", "dc"),
        Publisher("Image Comics", "imagecomics"),
        Publisher("Dark Horse Comics", "dark-horse-comics"),
        Publisher("IDW Publishing", "idw"),
        Publisher("Vertigo", "vertigo"),
        Publisher("WildStorm", "wildstorm"),
        Publisher("Dynamite Entertainment", "dynamite"),
        Publisher("Boom! Studios", "boomstudios"),
        Publisher("Avatar Press", "avatarpress"),
        Publisher("Fox Atomicg", "foxatomic"),
        Publisher("Top Shelf Productions", "topshelfproduct"),
        Publisher("Topps", "topps"),
        Publisher("Radical Publishing", "radical-publishing"),
        Publisher("Top Cow", "top-cow"),
        Publisher("Zenescope Entertainment", "zenescope"),
        Publisher("88MPH", "88mph"),
        Publisher("Soleil", "soleil"),
        Publisher("Warner Bros. Entertainment", "warner-bros"),
        Publisher("Ubisoft Entertainment", "ubisoft"),
        Publisher("Oni Press", "oni-press"),
        Publisher("Armada", "delcourt"),
        Publisher("Heavy Metal", "heavy-metal"),
        Publisher("Harris Comics", "harris-comics"),
        Publisher("Antarctic Press", "antarctic-press"),
        Publisher("Valiant", "valiant"),
        Publisher("Disney", "disney"),
        Publisher("Malibu", "malibu"),
        Publisher("Slave Labor", "slave-labor"),
        Publisher("Nbm", "nbm"),
        Publisher("Viper Comics", "viper-comics"),
        Publisher("Random House", "random-house"),
        Publisher("Active Images", "active-images"),
        Publisher("Eurotica", "eurotica"),
        Publisher("Vortex", "vortex"),
        Publisher("Fantagraphics", "fantagraphics"),
        Publisher("Epic", "epic"),
        Publisher("Warp Graphics", "warp-graphics"),
        Publisher("Scholastic Book Services", "scholastic-book-services"),
        Publisher("Ballantine Books", "ballantine-books"),
        Publisher("Id Software", "id-software"),
    )
    private val publishersName = getPublishersList().map {
        it.name
    }.toTypedArray()

    companion object {
        const val PREFIX_SLUG_SEARCH = "slug:"
        private const val PATH_URL = "/comics/series/"
        private const val PATH_online = "/comics/online/"
        private const val PATH_issue = "/comics/issue/"
        private const val PATH_publishers = "/comics/publishers"
        private const val PATH_events = "/comics/events"
    }
}
