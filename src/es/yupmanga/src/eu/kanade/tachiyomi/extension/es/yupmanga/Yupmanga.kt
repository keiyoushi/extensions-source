package eu.kanade.tachiyomi.extension.es.yupmanga

import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Yupmanga : HttpSource() {

    override val name = "Yupmanga"

    override val baseUrl = "https://www.yupmanga.com"

    override val lang = "es"

    override val supportsLatest = true

    // Cached CSRF token, populated by the token interceptor during normal browsing flow.
    private var csrfToken: String = ""

    // Peeks at every HTML response to extract the _token input value without consuming the body.
    private val tokenInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (response.header("Content-Type").orEmpty().contains("text/html")) {
            Jsoup.parse(response.peekBody(Long.MAX_VALUE).string())
                .selectFirst("input[name=_token]")
                ?.attr("value")
                ?.takeIf { it.isNotEmpty() }
                ?.let { csrfToken = it }
        }
        response
    }

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .addInterceptor(tokenInterceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("x-requested-with", "XMLHttpRequest")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/top", headers)

    override fun popularMangaParse(response: Response) = parseSeriesList(response.asJsoup())

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/?page=$page", headers)

    override fun latestUpdatesParse(response: Response) = parseSeriesList(response.asJsoup())

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.length < 3) {
            throw Exception("El término de búsqueda debe tener al menos 3 caracteres.")
        }
        val url = "$baseUrl/search.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        document.selectFirst("main > div.container > div[class^=bg-red]:has(p)")?.let {
            throw Exception("Límite de solicitudes alcanzado. Intente de nuevo en unos minutos.")
        }
        return parseSeriesList(document)
    }

    private fun parseSeriesList(document: Document): MangasPage {
        val mangas = document.selectFirst("div.grid:has(div.comic-card)")
            ?.select("div.comic-card")
            ?.mapNotNull { element ->
                val href = element.selectFirst("a[href]")?.attr("abs:href") ?: return@mapNotNull null
                val id = href.toHttpUrlOrNull()?.queryParameter("id") ?: return@mapNotNull null

                val title = element.selectFirst("h3")?.text()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                SManga.create().apply {
                    this.title = title
                    url = id
                    thumbnail_url = element.selectFirst("img.object-cover")?.attr("abs:src")
                }
            } ?: emptyList()
        val hasNextPage = document.selectFirst("div.flex > a:contains(Siguiente)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsRequest(manga: SManga) = GET("$baseUrl/series.php?id=${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            with(document.selectFirst("main > div.container")!!) {
                title = selectFirst("h1")!!.text()
                description = selectFirst("p#synopsisText")?.text()
                author = selectFirst("i[title=Editorial] + span")?.text()
                status = selectFirst("span:has(i[title=Estado])").parseStatus()
                genre = select("a.genre-tag").joinToString { genre ->
                    genre.text().replaceFirstChar { it.uppercase() }
                }
                thumbnail_url = document.selectFirst("meta[property=og:image]")!!.attr("content")
            }
        }
    }

    private fun Element?.parseStatus(): Int = when (this?.text()?.lowercase()) {
        "activo" -> SManga.ONGOING
        "finalizado" -> SManga.COMPLETED
        "abandonado" -> SManga.CANCELLED
        "pausado" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun paginatedChapterListRequest(mangaId: String, page: Int): Request {
        val url = "$baseUrl/ajax/load_chapters.php".toHttpUrl().newBuilder()
            .addQueryParameter("series_id", mangaId)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("order", "newest_first")

        return GET(url.build(), headers)
    }

    override fun chapterListRequest(manga: SManga): Request = paginatedChapterListRequest(manga.url, 1)

    override fun chapterListParse(response: Response): List<SChapter> {
        val allChapters = mutableListOf<SChapter>()
        val mangaId = response.request.url.queryParameter("series_id")!!

        lateinit var chapterListDto: ChapterListDto

        var page = 1
        do {
            chapterListDto = if (page == 1) {
                response.parseAs()
            } else {
                client.newCall(
                    paginatedChapterListRequest(mangaId, page),
                ).execute().parseAs()
            }

            val doc = Jsoup.parseBodyFragment(chapterListDto.html, baseUrl)
            allChapters.addAll(parseChapterList(doc, mangaId))

            page++
        } while (chapterListDto.hasNextPage())

        return allChapters
    }

    private fun parseChapterList(document: Document, mangaId: String): List<SChapter> = document.select("div.comic-card").map { element ->
        val chapterId = element.selectFirst("a[data-chapter]")!!.attr("data-chapter")

        SChapter.create().apply {
            name = element.selectFirst("h3")!!.text()
            url = "$chapterId#$mangaId"
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaId = if (chapter.url.startsWith("/ajax/")) {
            "$baseUrl${chapter.url}".toHttpUrlOrNull()?.queryParameter("s") ?: ""
        } else {
            chapter.url.substringAfter("#")
        }
        return "$baseUrl/series.php?id=$mangaId"
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId: String
        val mangaId: String

        // Handling backward compatibility for old SChapter.url format
        if (chapter.url.startsWith("/ajax/")) {
            val url = "$baseUrl${chapter.url}".toHttpUrl()
            chapterId = url.queryParameter("chapter") ?: throw Exception("ID de capítulo inválido")
            mangaId = url.queryParameter("s") ?: ""
        } else {
            chapterId = chapter.url.substringBefore("#")
            mangaId = chapter.url.substringAfter("#")
        }

        // The CSRF token is captured passively by tokenInterceptor during normal browsing —
        // no extra GET request needed here.
        val challengeUrl = "$baseUrl/ajax/get_challenge.php".toHttpUrl().newBuilder()
            .addQueryParameter("chapter", chapterId)
            .apply {
                if (mangaId.isNotEmpty()) addQueryParameter("s", mangaId)
            }
            .build()

        val challenge = client.newCall(GET(challengeUrl, headers)).execute().parseAs<ChallengeDto>()
        if (!challenge.success || challenge.challengeJs == null || challenge.challengeId == null) {
            throw Exception("Error fetching challenge")
        }

        // Broad mocking to avoid "cannot read property 'length' of undefined" in QuickJs evaluation.
        val answer = QuickJs.create().use {
            it.evaluate(
                """
                var mockElem = {
                    value: "$csrfToken",
                    getAttribute: function(attr) { return "$csrfToken"; },
                    dataset: { token: "$csrfToken" },
                    textContent: "$csrfToken",
                    innerText: "$csrfToken",
                    innerHTML: "$csrfToken",
                    id: "csrf_token",
                    name: "_token",
                    className: "_token"
                };
                var document = {
                    querySelector: function(sel) { return mockElem; },
                    getElementById: function(id) { return mockElem; },
                    getElementsByName: function(name) { return [mockElem]; },
                    getElementsByTagName: function(tag) { return [mockElem]; },
                    getElementsByClassName: function(cls) { return [mockElem]; },
                    cookie: ""
                };
                (function(){ ${challenge.challengeJs} })();
                """.trimIndent(),
            )?.toString()
        } ?: throw Exception("Failed to solve challenge")

        val chapterTokenUrl = "$baseUrl/ajax/get_reader_token.php".toHttpUrl().newBuilder()
            .addQueryParameter("chapter", chapterId)
            .addQueryParameter("challenge_id", challenge.challengeId)
            .addQueryParameter("answer", answer)
            .build()

        val tokenDto = client.newCall(GET(chapterTokenUrl, headers)).execute().parseAs<TokenDto>()
        if (!tokenDto.success || tokenDto.token.isNullOrEmpty()) {
            throw Exception("Información desactualizada. Refresque la lista de capítulos.")
        }

        val realChapterId = tokenDto.chapterId ?: chapterId
        val readerUrl = "$baseUrl/reader_v2.php".toHttpUrl().newBuilder()
            .addQueryParameter("chapter", realChapterId)
            .addQueryParameter("token", tokenDto.token)
            .addQueryParameter("page", "1")
            .build()

        return GET(readerUrl, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("div#readerContent img.page-image").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
