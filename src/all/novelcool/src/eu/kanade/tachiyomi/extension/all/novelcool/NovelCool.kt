package eu.kanade.tachiyomi.extension.all.novelcool

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class NovelCool :
    KeiSource(),
    ConfigurableSource {

    private val siteLang: String get() = if (lang == "pt-BR") "br" else lang

    private val apiUrl = "https://api.novelcool.com"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1)

    private val pageClient by lazy {
        client.newBuilder()
            .addInterceptor(::jsRedirect)
            .build()
    }

    private val preference by getPreferencesLazy()

    override suspend fun getPopularManga(page: Int): MangasPage = when (preference.useAppApi) {
        true -> commonApiRequest("$apiUrl/elite/hot/", page)
        else -> parseMangasPage(client.get("$baseUrl/category/new_list.html").asJsoup())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = when (preference.useAppApi) {
        true -> commonApiRequest("$apiUrl/elite/latest/", page)
        else -> parseMangasPage(client.get("$baseUrl/category/latest.html").asJsoup())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (preference.useAppApi) {
            return commonApiRequest("$apiUrl/book/search/", page, query)
        }

        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("name", query.trim())

            filters.forEach { filter ->
                when (filter) {
                    is AuthorFilter -> {
                        addQueryParameter("author", filter.state.trim())
                    }

                    is GenreFilter -> {
                        addQueryParameter("category_id", filter.included.joinToString(",", ","))
                        addQueryParameter("out_category_id", filter.excluded.joinToString(",", ","))
                    }

                    is StatusFilter -> {
                        addQueryParameter("completed_series", filter.getValue())
                    }

                    is RatingFilter -> {
                        addQueryParameter("rate_star", filter.getValue())
                    }

                    else -> { }
                }
            }

            addQueryParameter("page", page.toString())
        }.build()

        return parseMangasPage(client.get(url).asJsoup())
    }

    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select(".book-list .book-item:not(:has(.book-type-novel))").map { element ->
            SManga.create().apply {
                title = element.select(".book-pic").attr("title")
                setUrlWithoutDomain(element.select("a").attr("href"))
                thumbnail_url = element.select("img").imgAttr()
            }
        }
        val hasNextPage = document.selectFirst("div.page-nav a div.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override val supportsFilterFetching get() = !preference.useAppApi

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/search/").asJsoup()

        return document.selectFirst(".category-list")
            ?.select(".category-id-item")
            .orEmpty()
            .map { div ->
                Pair(
                    div.attr("title"),
                    div.attr("cate_id"),
                )
            }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        if (preference.useAppApi) {
            return FilterList(Filter.Header("Not supported when using App API"))
        }

        val filters: MutableList<Filter<*>> = mutableListOf(
            AuthorFilter("Author"),
            StatusFilter("Status", getStatusList()),
            RatingFilter("Rating", getRatingList()),
        )

        data?.parseAs<List<Pair<String, String>>>()?.also {
            filters.add(GenreFilter("Genres", it))
        }

        return FilterList(filters)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("h1.bookinfo-title")!!.text()
            description = document.selectFirst("div.bk-summary-txt")?.text()
            genre = document.select(".bookinfo-category-list a").joinToString { it.text() }
            author = document.selectFirst(".bookinfo-author > a")?.attr("title")
            thumbnail_url = document.selectFirst(".bookinfo-pic-img")?.attr("abs:src")
            status = document.select(".bookinfo-category-list a").first()?.text().parseStatus()
        }

        val chapterList = document.select(".chapter-item-list a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                name = element.attr("title")
                date_upload = DATE_FORMATTER.tryParseDate(element.select(".chapter-item-time").text())
            }
        }

        return SMangaUpdate(details, chapterList)
    }

    private fun String?.parseStatus(): Int {
        this ?: return SManga.UNKNOWN
        return when {
            this.lowercase() in completedStatusList -> SManga.COMPLETED
            this.lowercase() in ongoingStatusList -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        var doc = pageClient.get(getChapterUrl(chapter)).asJsoup()

        // Chapter pages redirect (HTTP 302) to an intermediate "choose a source" page on a
        // partner domain (e.g. techsmartideas.com). That page contains a.vision-button links
        // which point to the actual image server (e.g. financemasterpro.com). This is the
        // same shared infrastructure used by NineAnime.
        val serverUrl = doc.selectFirst("a.vision-button")?.attr("abs:href")

        if (serverUrl != null) {
            val serverHeaders = headers.newBuilder()
                .set("Referer", doc.baseUri())
                .build()
            doc = pageClient.get(serverUrl, serverHeaders).asJsoup()
        }

        // Parse all_imgs_url from the script using a robust approach: extract the array
        // content as a string and then find all quoted http URLs within it. This avoids
        // fragile JSON parsing and trailing-comma issues in the original JS array.
        val scriptData = doc.select("script:containsData(all_imgs_url)").firstOrNull()?.data()

        if (scriptData != null) {
            val arrayContent = scriptData
                .substringAfter("all_imgs_url: [")
                .substringBefore("]")
            val images = imageUrlRegex.findAll(arrayContent)
                .map { it.groupValues[1].replace("\\/", "/") }
                .toList()

            if (images.isNotEmpty()) {
                return images.mapIndexed { idx, img -> Page(idx, imageUrl = img) }
            }
        }

        return singlePageParse(doc)
    }

    private fun singlePageParse(document: Document): List<Page> = document.selectFirst(".mangaread-pagenav > .sl-page")?.select("option")
        ?.mapIndexed { idx, page ->
            Page(idx, url = page.attr("value"))
        } ?: emptyList()

    override suspend fun getImageUrl(page: Page): String {
        val document = client.get(page.url).asJsoup()
        return document.select(".mangaread-manga-pic").attr("src")
    }

    private fun Elements.imgAttr(): String = when {
        hasAttr("lazy_url") -> attr("abs:lazy_url")
        else -> attr("abs:src")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_API_SEARCH
            title = "Use App API for browse"
            summary = "Results may be more reliable"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    private val SharedPreferences.useAppApi: Boolean
        get() = getBoolean(PREF_API_SEARCH, true)

    private fun jsRedirect(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val headers = request.headers.newBuilder()
            .removeAll("Accept-Encoding")
            .build()
        val response = chain.proceed(request.newBuilder().headers(headers).build())

        if (response.header("Content-Type")?.contains("text/html") != true) {
            return response
        }

        val responseBody = response.peekBody(Long.MAX_VALUE).string()
        val document = Jsoup.parse(responseBody)
        val script = document.selectFirst("script:containsData(window.location.href)")?.html()
            ?: return response

        val jsRedirect = JS_REDIRECT_REGEX.find(script)?.groupValues?.get(1)
            ?: return response

        val requestUrl = response.request.url

        val url = requestUrl.resolve(jsRedirect)
            ?: return response

        response.close()

        val newHeaders = request.headers.newBuilder()
            .set("Referer", requestUrl.toString())
            .build()

        return chain.proceed(
            request.newBuilder()
                .url(url)
                .headers(newHeaders)
                .build(),
        )
    }

    private suspend fun commonApiRequest(url: String, page: Int, query: String? = null): MangasPage {
        val payload = NovelCoolBrowsePayload(
            appId = APP_ID,
            lang = siteLang,
            query = query,
            type = "manga",
            page = page.toString(),
            size = SIZE.toString(),
            secret = APP_SECRET,
        )

        val browse = client.post(url, payload.toJsonRequestBody()).parseAs<NovelCoolBrowseResponse>()
        val mangas = browse.list?.map {
            it.toSManga().apply {
                setUrlWithoutDomain(it.url)
            }
        }.orEmpty()

        return MangasPage(mangas, browse.list?.size == SIZE)
    }

    companion object {
        private const val APP_ID = "202201290625004"
        private const val APP_SECRET = "c73a8590641781f203660afca1d37ada"
        private const val SIZE = 20

        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

        // Matches any http/https URL inside single or double quotes within the all_imgs_url array.
        // Using the same approach as NineAnime which shares the same image-serving infrastructure.
        private val imageUrlRegex = Regex("""["'](https?://[^"']+)["']""")

        private val JS_REDIRECT_REGEX = Regex("""window\.location\.href\s*=\s*["']([^"']+)["']""")

        private const val PREF_API_SEARCH = "pref_use_search_api"

        // copied from Madara
        private val completedStatusList: Array<String> = arrayOf(
            "completed",
            "completo",
            "completado",
            "concluído",
            "concluido",
            "finalizado",
            "terminé",
            "hoàn thành",
        )

        private val ongoingStatusList: Array<String> = arrayOf(
            "ongoing", "Продолжается", "updating", "em lançamento", "em lançamento", "em andamento",
            "em andamento", "en cours", "ativo", "lançando", "Đang Tiến Hành", "devam ediyor",
            "devam ediyor", "in corso", "in arrivo", "en curso", "en curso", "emision",
            "curso", "en marcha", "Publicandose", "en emision",
        )
    }
}
