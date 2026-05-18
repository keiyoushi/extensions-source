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

    // Cached CSRF token and data-k value, populated by the token interceptor during normal browsing flow.
    private var csrfToken: String = ""
    private var dataK: String = ""
    private var dataV: String = ""
    private var anchorScript: String = ""

    // Peeks at every HTML response to extract the token and data-k values without consuming the body.
    private val tokenInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (response.header("Content-Type").orEmpty().contains("text/html")) {
            // Peak a max of 3MB of data to prevent OutOfMemoryError
            val html = response.peekBody(3 * 1024 * 1024L).string()

            TOKEN_REGEX.find(html)?.groupValues?.get(1)?.let { csrfToken = it }
            TOKEN_META_REGEX.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { csrfToken = it }
            TOKEN_JS_REGEX.find(html)?.groupValues?.get(1)?.let { match ->
                csrfToken = match.split(",").mapNotNull {
                    val clean = it.trim()
                    if (clean.startsWith("0x", ignoreCase = true)) {
                        clean.substring(2).toIntOrNull(16)?.toChar()
                    } else {
                        clean.toIntOrNull()?.toChar()
                    }
                }.joinToString("")
            }
            DATAK_REGEX.find(html)?.groupValues?.get(1)?.let { dataK = it }
            DATAV_REGEX.find(html)?.groupValues?.get(1)?.let { match ->
                dataV = match.split(",").mapNotNull {
                    val clean = it.trim()
                    if (clean.startsWith("0x", ignoreCase = true)) {
                        clean.substring(2).toIntOrNull(16)?.toChar()
                    } else {
                        clean.toIntOrNull()?.toChar()
                    }
                }.joinToString("")
            }
            ANCHOR_SCRIPT_REGEX.find(html)?.groupValues?.get(1)?.let { anchorScript = it }
        }
        response
    }

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .addInterceptor(tokenInterceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val apiHeaders by lazy {
        headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()
    }

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
            val container = document.selectFirst("main > div.container")
                ?: throw Exception("No se pudo encontrar la información del manga")

            title = container.selectFirst("h1")?.text()
                ?: throw Exception("Título del manga no encontrado")
            description = container.selectFirst("p#synopsisText")?.text()
            author = container.selectFirst("i[title=Editorial] + span")?.text()
            status = container.selectFirst("span:has(i[title=Estado])").parseStatus()
            genre = container.select("a.genre-tag").joinToString { genre ->
                genre.text().replaceFirstChar { it.uppercase() }
            }
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
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

        return GET(url.build(), apiHeaders)
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

    private fun parseChapterList(document: Document, mangaId: String): List<SChapter> {
        return document.select("div.comic-card").mapNotNull { element ->
            val chapterId = element.selectFirst("a[data-chapter]")?.attr("data-chapter")
                ?: return@mapNotNull null
            val chapterName = element.selectFirst("h3")?.text()
                ?: return@mapNotNull null

            SChapter.create().apply {
                name = chapterName
                url = "$chapterId#$mangaId"
            }
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

        // If opened directly from the library/cached details and csrfToken is empty,
        // we must fetch the series page to trigger the interceptor and populate the values.
        if (csrfToken.isEmpty() && mangaId.isNotEmpty()) {
            client.newCall(GET("$baseUrl/series.php?id=$mangaId", headers)).execute().close()
        }

        val challengeUrl = "$baseUrl/ajax/get_challenge.php".toHttpUrl().newBuilder()
            .addQueryParameter("chapter", chapterId)
            .apply {
                if (mangaId.isNotEmpty()) addQueryParameter("s", mangaId)
            }
            .build()

        val challenge = client.newCall(GET(challengeUrl, apiHeaders)).execute().parseAs<ChallengeDto>()
        if (!challenge.success || challenge.challengeJs == null || challenge.challengeId == null) {
            throw Exception("Error fetching challenge")
        }

        // Broad mocking to avoid "cannot read property" crashes in QuickJs evaluation.
        val answer = QuickJs.create().use {
            it.evaluate(
                """
                var cssVars = {};
                var window = {
                    getComputedStyle: function(el) {
                        return {
                            getPropertyValue: function(prop) {
                                return cssVars[prop] || "$csrfToken";
                            }
                        };
                    }
                };
                var mockElem = {
                    value: "$csrfToken",
                    content: "$csrfToken",
                    getAttribute: function(attr) {
                        if (attr === 'data-k') return "$dataK";
                        if (attr === 'data-v') return "${dataV.ifEmpty { csrfToken }}";
                        if (attr === 'content') return "$csrfToken";
                        return "$csrfToken";
                    },
                    dataset: { token: "$csrfToken", k: "$dataK", v: "$dataV" },
                    textContent: "$csrfToken",
                    innerText: "$csrfToken",
                    innerHTML: "$csrfToken",
                    id: "csrf_token",
                    name: "_token",
                    className: "_cr",
                    style: {
                        setProperty: function(prop, val) {
                            cssVars[prop] = val;
                        }
                    }
                };
                var document = {
                    documentElement: mockElem,
                    querySelector: function(sel) { return mockElem; },
                    getElementById: function(id) { return mockElem; },
                    getElementsByName: function(name) { return [mockElem]; },
                    getElementsByTagName: function(tag) { return [mockElem]; },
                    getElementsByClassName: function(cls) { return [mockElem]; },
                    cookie: ""
                };

                try {
                    $anchorScript
                } catch(e) {}

                (function(){ ${challenge.challengeJs} })();
                """.trimIndent(),
            )?.toString()
        } ?: throw Exception("Failed to solve challenge")

        val chapterTokenUrl = "$baseUrl/ajax/get_reader_token.php".toHttpUrl().newBuilder()
            .addQueryParameter("chapter", chapterId)
            .addQueryParameter("challenge_id", challenge.challengeId)
            .addQueryParameter("answer", answer)
            .build()

        val tokenDto = client.newCall(GET(chapterTokenUrl, apiHeaders)).execute().parseAs<TokenDto>()
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
        val requestUrl = response.request.url

        val chapterId = requestUrl.queryParameter("chapter")
        val token = requestUrl.queryParameter("token")

        // In 'Manga' mode, only the first image is present in the DOM, so we must rely on the inline config script.
        // This will successfully generate the pages accurately for both Webtoon & Manga modes.
        val scriptData = document.selectFirst("script:containsData(totalPages:)")?.data()

        if (scriptData != null && chapterId != null && token != null) {
            val totalPagesStr = scriptData.substringAfter("totalPages:").substringBefore(",").trim()
            val totalPages = totalPagesStr.toIntOrNull()

            if (totalPages != null && totalPages > 0) {
                return (1..totalPages).map { pageNum ->
                    val imageUrl = "$baseUrl/image-proxy-v2.php?chapter=$chapterId&page=$pageNum&token=$token&context=reader"
                    Page(pageNum - 1, imageUrl = imageUrl)
                }
            }
        }

        // Fallback to DOM scraping just in case
        return document.select("div#readerContent img.page-image").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        private val TOKEN_REGEX = """id=["']csrf_token["']\s+value=["']([^"']+)["']""".toRegex()
        private val TOKEN_META_REGEX = """name=["']csrf-token["'][^>]*?content=["']([^"']+)["']""".toRegex()
        private val TOKEN_JS_REGEX = """(?:_token|csrf-token).*?String\.fromCharCode\(([^)]+)\)""".toRegex()
        private val DATAK_REGEX = """id=["']app-cfg["']\s+data-k=["']([^"']+)["']""".toRegex()
        private val DATAV_REGEX = """['"]data-v['"].*?String\.fromCharCode\(([^)]+)\)""".toRegex()
        private val ANCHOR_SCRIPT_REGEX = """<script>\s*(\(\s*function\(\)\s*\{\s*document\.querySelector\(':root'\)[\s\S]*?\}\s*\)\(\);?)\s*</script>""".toRegex()
    }
}
