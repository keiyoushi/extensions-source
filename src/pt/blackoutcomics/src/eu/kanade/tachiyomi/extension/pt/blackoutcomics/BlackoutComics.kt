package eu.kanade.tachiyomi.extension.pt.blackoutcomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class BlackoutComics : HttpSource() {

    override val name = "Blackout Comics"

    override val baseUrl = "https://blackoutcomics.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    private val baseHttpUrl = baseUrl.toHttpUrl()

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::ageGateInterceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("DNT", "1")
        .add("Sec-GPC", "1")
        .add("Upgrade-Insecure-Requests", "1")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        .add("Accept-Language", "pt-BR,pt;q=0.8,en-US;q=0.5,en;q=0.3")
        .add("Sec-Fetch-Dest", "document")
        .add("Sec-Fetch-Mode", "navigate")
        .add("Sec-Fetch-Site", "same-origin")

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ranking", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select(".ranking-grid a.webtoon-card").map { el ->
            SManga.create().apply {
                setUrlWithoutDomain(el.attr("abs:href"))
                title = el.select(".card-title span").text()
                thumbnail_url = el.select(".card-thumb img").attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/atualizados-recente?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select(".webtoon-grid a.webtoon-card").map { el ->
            SManga.create().apply {
                setUrlWithoutDomain(el.attr("abs:href"))
                title = el.select(".card-title span").text()
                thumbnail_url = el.select(".card-thumb img").attr("abs:src")
            }
        }
        val hasNext = doc.select(".pagerx__link[rel=next]").isNotEmpty()
        return MangasPage(mangas, hasNext)
    }

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/comics".toHttpUrl().newBuilder()
                .addQueryParameter("src", query)
                .addQueryParameter("format", "json")
                .build()
            return GET(url, headers)
        }

        val url = "$baseUrl/comics".toHttpUrl().newBuilder()
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()

        if (!status.isNullOrEmpty()) url.addQueryParameter("status", status)
        if (!genre.isNullOrEmpty()) url.addQueryParameter("gen", genre)

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = if (response.request.url.queryParameter("format") == "json") {
        val searchResponse = response.parseAs<SearchResponse>()
        val mangas = searchResponse.items.map { it.toSManga(baseUrl) }
        MangasPage(mangas, false)
    } else {
        val doc = response.asJsoup()
        val mangas = doc.select(".webtoon-grid a.webtoon-card").map { el ->
            SManga.create().apply {
                setUrlWithoutDomain(el.attr("abs:href"))
                title = el.select(".card-title span").text()
                thumbnail_url = el.select(".card-thumb img").attr("abs:src")
            }
        }
        MangasPage(mangas, false)
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()
        return SManga.create().apply {
            title = doc.select(".project-title").text()
            thumbnail_url = doc.select(".project-cover").attr("abs:src")
            author = doc.select(".quick-info-item:has(.fa-pen-nib) strong").text()
            artist = doc.select(".quick-info-item:has(.fa-palette) strong").text()
            description = doc.select(".project-description").text()
            genre = doc.select(".project-genres .genre-tag").joinToString { it.text() }

            val statusText = doc.select(".status-pill").text().lowercase()
            status = when {
                statusText.contains("lançamento") -> SManga.ONGOING
                statusText.contains("completo") -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val mangaUrl = response.request.url.encodedPath

        return doc.select("#tab-capitulos-list .normal_ep").map { el ->
            SChapter.create().apply {
                val linkElement = el.selectFirst("a[href]")
                val num = el.select(".num").text()

                if (linkElement != null) {
                    setUrlWithoutDomain(linkElement.attr("abs:href"))
                } else {
                    url = "$mangaUrl/ler/capitulo-$num"
                }

                var chapterName = "Capítulo $num"
                val title = el.select(".cell-title strong.line-3").text()
                if (title.isNotEmpty()) {
                    chapterName += " - $title"
                }
                name = chapterName

                date_upload = dateFormat.tryParse(el.select(".cell-num .text-muted").text())
            }
        }
    }

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()

        for (script in doc.select("script:not([src])")) {
            val match = PAGE_LIST_REGEX.find(script.html()) ?: continue
            val urls = match.groupValues[1].parseAs<List<String>>()
            return urls.mapIndexed { i, url ->
                Page(i, imageUrl = if (url.startsWith("http")) url else "$baseUrl$url")
            }
        }

        if (doc.html().contains("showLoginModal()")) {
            throw Exception(
                "Necessário fazer login. Abra o site no WebView (ícone de navegador " +
                    "no canto superior direito), faça login com sua conta e tente novamente.",
            )
        }
        throw Exception("Nenhuma página encontrada ou estrutura do site foi alterada.")
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .removeHeader("Referer")
        .removeHeader("Upgrade-Insecure-Requests")
        .removeHeader("Sec-Fetch-Dest")
        .removeHeader("Sec-Fetch-Mode")
        .removeHeader("Sec-Fetch-Site")
        .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
        .header("Sec-Fetch-Dest", "image")
        .header("Sec-Fetch-Mode", "no-cors")
        .header("Sec-Fetch-Site", "same-origin")
        .build()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================
    override fun getFilterList() = FilterList(
        StatusFilter(),
        GenreFilter(),
    )

    // ============================== Utilities =============================
    private fun ageGateInterceptor(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url

        if (url.host == baseHttpUrl.host) {
            val cookies = client.cookieJar.loadForRequest(url)
            if (cookies.none { it.name == "age_gate_consent" }) {
                val ageCookie = Cookie.Builder()
                    .name("age_gate_consent")
                    .value("{\"consentAt\":1777661090431,\"expiresAt\":1778265890431}")
                    .domain(url.host)
                    .path("/")
                    .build()

                val popCookie = Cookie.Builder()
                    .name("_popprepop")
                    .value("1")
                    .domain(url.host)
                    .path("/")
                    .build()

                client.cookieJar.saveFromResponse(url, listOf(ageCookie, popCookie))
            }
        }
        return chain.proceed(original)
    }

    companion object {
        private val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.ROOT)

        private val PAGE_LIST_REGEX = Regex("""S\s*=\s*(\[[\s\S]*?])""")
    }
}
