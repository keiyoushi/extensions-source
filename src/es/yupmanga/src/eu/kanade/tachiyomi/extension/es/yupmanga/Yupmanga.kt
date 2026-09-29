package eu.kanade.tachiyomi.extension.es.yupmanga

import app.cash.quickjs.QuickJs
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
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class Yupmanga : KeiSource() {

    // Cached CSRF token populated by the token interceptor during normal browsing flow.
    private var csrfToken: String = ""

    // Peeks at every HTML response to extract the token values without consuming the body.
    private val tokenInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (response.header("Content-Type").orEmpty().contains("text/html")) {
            // Peak a max of 3MB of data to prevent OutOfMemoryError
            val html = response.peekBody(3 * 1024 * 1024L).string()

            val token = TOKEN_REGEX.find(html)?.groupValues?.get(1)
                ?: TOKEN_META_REGEX.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                ?: TOKEN_JS_REGEX.decodeChars(html).takeIf { it.isNotBlank() }

            if (!token.isNullOrEmpty()) {
                csrfToken = token
            }
        }
        response
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(tokenInterceptor)
        .rateLimit(1)

    private val apiHeaders: Headers
        get() = headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

    override suspend fun getPopularManga(page: Int): MangasPage = parseSeriesList(client.get("$baseUrl/top").asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseSeriesList(client.get("$baseUrl/?page=$page").asJsoup())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.length < 3) {
            throw Exception("El término de búsqueda debe tener al menos 3 caracteres.")
        }
        val url = "$baseUrl/search.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()

        val document = client.get(url).asJsoup()
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

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series.php?id=${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) async { fetchMangaDetails(manga) } else null
        val chapterList = if (fetchChapters) async { fetchChapterList(manga.url) } else null

        SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return manga.apply {
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

    private suspend fun fetchChapterList(mangaId: String): List<SChapter> {
        val allChapters = mutableListOf<SChapter>()

        var page = 1
        do {
            val url = "$baseUrl/ajax/load_chapters.php".toHttpUrl().newBuilder()
                .addQueryParameter("series_id", mangaId)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("order", "newest_first")
                .build()

            val chapterListDto = client.get(url, apiHeaders).parseAs<ChapterListDto>()

            val doc = Jsoup.parseBodyFragment(chapterListDto.html, baseUrl)
            allChapters.addAll(parseChapterList(doc, mangaId))

            page++
        } while (chapterListDto.hasNextPage())

        return allChapters
    }

    private fun parseChapterList(document: Document, mangaId: String): List<SChapter> = document.select("div.comic-card").mapNotNull { element ->
        val chapterId = element.selectFirst("a[data-chapter]")?.attr("data-chapter")
            ?: return@mapNotNull null
        val chapterName = element.selectFirst("h3")?.text()
            ?: return@mapNotNull null

        SChapter.create().apply {
            name = chapterName
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

    override suspend fun getPageList(chapter: SChapter): List<Page> {
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
            client.get("$baseUrl/series.php?id=$mangaId").close()
        }

        val requestHeaders = apiHeaders.newBuilder().apply {
            if (csrfToken.isNotEmpty()) {
                add("X-CSRF-Token", csrfToken)
            }
        }.build()

        val anchorUrl = "$baseUrl/ajax/get_anchor.php?s=$mangaId"
        val anchorValue = client.get(anchorUrl, requestHeaders).parseAs<AnchorDto>().v ?: throw Exception("Failed to get anchor")

        val challengeBody = FormBody.Builder()
            .add("chapter", chapterId)
            .apply {
                if (mangaId.isNotEmpty()) add("s", mangaId)
            }
            .build()
        val challengeUrl = "$baseUrl/ajax/get_challenge.php"

        val challenge = client.post(challengeUrl, apiHeaders, challengeBody).parseAs<ChallengeDto>()
        if (!challenge.success || challenge.challengeJs == null || challenge.challengeId == null) {
            throw Exception("Error fetching challenge")
        }

        val sanitizedJs = challenge.challengeJs
            .replace("return (async function(){", "return (function(){")
            .replace(INNER_CHALLENGE_REGEX, "\"$anchorValue\"")

        val answer = QuickJs.create().use {
            it.evaluate(
                """
                var document = {
                    getElementById: function(id) {
                        if (id === 'ym-f2') {
                            return {
                                elements: {
                                    'k2': { value: "$anchorValue" }
                                }
                            };
                        }
                        return null;
                    }
                };
                var window = {};
                var atob = function(input) {
                    var chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
                    var str = String(input).replace(/=+$/, '');
                    var output = '';
                    for (var bc = 0, bs, fn, idx = 0; idx < str.length; ) {
                        var char = str.charAt(idx++);
                        var index = chars.indexOf(char);
                        if (index === -1) continue;
                        bs = bc % 4 ? bs * 64 + index : index;
                        if (bc++ % 4) {
                            output += String.fromCharCode(255 & bs >> (-2 * bc & 6));
                        }
                    }
                    return output;
                };
                (function() {
                    $sanitizedJs
                })();
                """.trimIndent(),
            )?.toString()
        } ?: throw Exception("Failed to solve challenge")

        val formBody = FormBody.Builder()
            .add("chapter", chapterId)
            .add("challenge_id", challenge.challengeId)
            .add("answer", answer)
            .build()

        val tokenDto = client.post("$baseUrl/ajax/get_reader_token.php", apiHeaders, formBody).parseAs<TokenDto>()
        if (!tokenDto.success || tokenDto.token.isNullOrEmpty()) {
            throw Exception("Información desactualizada. Refresque la lista de capítulos.")
        }

        val realChapterId = tokenDto.chapterId ?: chapterId
        val readerUrl = "$baseUrl/reader_v2.php".toHttpUrl().newBuilder()
            .addQueryParameter("chapter", realChapterId)
            .addQueryParameter("token", tokenDto.token)
            .addQueryParameter("page", "1")
            .build()

        val response = client.get(readerUrl)
        val requestUrl = response.request.url
        val document = response.asJsoup()

        val readerChapterId = requestUrl.queryParameter("chapter")
        val token = requestUrl.queryParameter("token")

        // In 'Manga' mode, only the first image is present in the DOM, so we must rely on the inline config script.
        // This will successfully generate the pages accurately for both Webtoon & Manga modes.
        val scriptData = document.selectFirst("script:containsData(totalPages:)")?.data()

        if (scriptData != null && readerChapterId != null && token != null) {
            val totalPagesStr = scriptData.substringAfter("totalPages:").substringBefore(",").trim()
            val totalPages = totalPagesStr.toIntOrNull()

            if (totalPages != null && totalPages > 0) {
                return (1..totalPages).map { pageNum ->
                    val imageUrl = "$baseUrl/image-proxy-v2.php?chapter=$readerChapterId&page=$pageNum&token=$token&context=reader"
                    Page(pageNum - 1, imageUrl = imageUrl)
                }
            }
        }

        // Fallback to DOM scraping just in case
        return document.select("div#readerContent img.page-image").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }
    }

    private fun Regex.decodeChars(html: String): String = find(html)?.groupValues?.get(1)?.split(",")?.mapNotNull {
        val clean = it.trim()
        if (clean.startsWith("0x", ignoreCase = true)) {
            clean.substring(2).toIntOrNull(16)?.toChar()
        } else {
            clean.toIntOrNull()?.toChar()
        }
    }?.joinToString("") ?: ""

    companion object {
        private val TOKEN_REGEX = """id=["']csrf_token["']\s+value=["']([^"']+)["']""".toRegex()
        private val TOKEN_META_REGEX = """name=["']csrf-token["'][^>]*?content=["']([^"']+)["']""".toRegex()
        private val TOKEN_JS_REGEX = """(?:_token|csrf-token).*?String\.fromCharCode\(([^)]+)\)""".toRegex()
        private val INNER_CHALLENGE_REGEX = """await\s+\(async\s+function\(\)\{[\s\S]+?\}\)\(\)""".toRegex()
    }
}
