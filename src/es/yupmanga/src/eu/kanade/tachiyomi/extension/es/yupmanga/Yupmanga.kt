package eu.kanade.tachiyomi.extension.es.yupmanga

import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Yupmanga : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(1)

    private val apiHeaders: Headers
        get() = headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

    override suspend fun getPopularManga(page: Int): MangasPage = parseSeriesList(client.get("$baseUrl/top", Fetch.DOCUMENT).asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseSeriesList(client.get("$baseUrl/?page=$page", Fetch.DOCUMENT).asJsoup())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.length < 3) {
            throw Exception("El término de búsqueda debe tener al menos 3 caracteres.")
        }
        val url = "$baseUrl/search.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
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
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
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

            val chapterListDto = client.get(url, apiHeaders, Fetch.XHR).parseAs<ChapterListDto>()

            val doc = Jsoup.parseBodyFragment(chapterListDto.html, baseUrl)
            allChapters.addAll(parseChapterList(doc, mangaId))

            page++
        } while (chapterListDto.hasNextPage())

        return allChapters
    }

    // A chapter is kept by its number key: the data-chapter id changes on each visit of the site
    private fun parseChapterList(document: Document, mangaId: String): List<SChapter> = document.select("div.comic-card").mapNotNull { element ->
        val chapterKey = element.selectFirst("a[data-chapter-key]")?.attr("data-chapter-key")
            ?.takeIf(CHAPTER_KEY_REGEX::matches)
            ?: return@mapNotNull null
        val chapterName = element.selectFirst("h3")?.text()
            ?: return@mapNotNull null

        SChapter.create().apply {
            name = chapterName
            url = "$chapterKey#$mangaId"
            chapter_number = chapterKey.toFloat()
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
        val chapterKey = chapter.url.substringBefore("#")
        val seriesId = chapter.url.substringAfter("#", "")
        // Chapters listed before the site keyed them by number hold an id that it no longer accepts
        if (!CHAPTER_KEY_REGEX.matches(chapterKey) || seriesId.isEmpty()) throw Exception(OUTDATED)

        // Each visit of the series page renders anew the hidden element whose styles the challenge reads
        val seriesUrl = getChapterUrl(chapter)
        val seriesPage = client.get(seriesUrl, Fetch.DOCUMENT).asJsoup()
        val challenge = client.post("$baseUrl/ajax/get_challenge.php", apiHeaders, Fetch.XHR.from(seriesUrl), chapterForm(chapterKey, seriesId).build())
            .parseAs<ChallengeDto>()
        val challengeId = challenge.challengeId
        val challengeJs = challenge.challengeJs
        if (!challenge.success || challengeId == null || challengeJs == null) throw Exception("Error fetching challenge")

        val form = chapterForm(chapterKey, seriesId)
            .add("challenge_id", challengeId)
            .add("answer", solveChallenge(challengeJs, seriesPage))
            .build()
        val response = client.post("$baseUrl/ajax/open_chapter.php", apiHeaders, Fetch.XHR.from(seriesUrl), form, ensureSuccess = false)
        val access = response.parseAs<TokenDto>()
        // Guests can only open a limited number of chapters
        if (response.code == 429) throw Exception(access.message ?: "Límite de lectura alcanzado")
        val token = access.token?.takeIf { access.success } ?: throw Exception(access.error ?: OUTDATED)
        val chapterId = access.chapterId ?: throw Exception(OUTDATED)

        val readerUrl = "$baseUrl/reader_v2.php".toHttpUrl().newBuilder()
            .addQueryParameter("chapter", chapterId)
            .addQueryParameter("token", token)
            .addQueryParameter("page", "1")
            .build()
        var reader = client.get(readerUrl, Fetch.DOCUMENT.from(seriesUrl)).asJsoup()
        // The reader is sometimes behind an ad page, which lets it through once its countdown is over
        if (reader.location().toHttpUrl().encodedPath == "/box-modal.php") {
            passInterstitial(reader)
            reader = client.get(readerUrl, Fetch.DOCUMENT.from(reader.location())).asJsoup()
        }

        // In 'Manga' mode only the first image is in the DOM, so the pages come from the inline config script
        val totalPages = reader.selectFirst("script:containsData(totalPages:)")?.data()
            ?.let { TOTAL_PAGES_REGEX.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: throw Exception("No se pudo cargar el capítulo")
        val images = "$baseUrl/image-proxy-v2.php".toHttpUrl()
        return (1..totalPages).map { page ->
            val url = images.newBuilder()
                .addQueryParameter("chapter", chapterId)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("token", token)
                .addQueryParameter("context", "reader")
                .build()
            Page(page - 1, imageUrl = url.toString())
        }
    }

    private fun chapterForm(chapterKey: String, seriesId: String) = FormBody.Builder()
        .add("chapter_key", chapterKey)
        .add("s", seriesId)

    // The ad page's ticket only counts once its countdown has run, which the site checks on its server
    private suspend fun passInterstitial(page: Document) {
        val html = page.html()
        val ticket = AD_TICKET_REGEX.find(html)?.groupValues?.get(1)?.parseAs<AdTicketDto>()
            ?: throw Exception("No se pudo cargar el capítulo")
        delay((COUNTDOWN_REGEX.find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 8).seconds)
        repeat(3) {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("identifier", "chapter_entrance")
                .addFormDataPart("chapter", ticket.chapter)
                .addFormDataPart("nonce", ticket.nonce)
                .build()
            val response = client.post("$baseUrl/ajax/complete_ad_interstitial.php", apiHeaders, Fetch.XHR.from(page.location()), body, ensureSuccess = false)
            response.close()
            // 409: the server's clock hasn't reached the end of the countdown yet
            if (response.code != 409) return
            delay(1500.milliseconds)
        }
    }

    // The challenge sorts the children of a hidden element by their CSS order and reads their flex values. The page's
    // rules are made to mislead a plain reading (decoys for missing classes, !important, two classes over one), so the
    // cascade is worked out here, and the challenge runs against a stand-in for document and window.
    private fun solveChallenge(challengeJs: String, page: Document): String {
        val script = """
            var dom = ${hiddenStyles(page)};
            var defaults = { "order": "0", "flex-grow": "0", "flex-shrink": "1" };
            function node(o) { return { children: (o.children || []).map(node), style: o.style || {} }; }
            var document = { getElementById: function(id) { return dom[id] ? node(dom[id]) : null; } };
            var window = {
                getComputedStyle: function(e) {
                    return { getPropertyValue: function(p) { return p in e.style ? e.style[p] : (defaults[p] || ""); } };
                }
            };
            (function() { $challengeJs })();
        """.trimIndent()
        return QuickJs.create().use { it.evaluate(script)?.toString() } ?: throw Exception("Failed to solve challenge")
    }

    // For each element that the page's rules target by id, the declarations its children end up with
    private fun hiddenStyles(page: Document): String {
        val css = page.select("style").joinToString("\n") { it.data() }.replace(CSS_COMMENT_REGEX, "")
        val declarations = CSS_RULE_REGEX.findAll(css).withIndex().flatMap { (index, rule) ->
            val properties = rule.groupValues[2].split(';').mapNotNull { declaration ->
                val parts = declaration.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                Triple(parts[0].trim().lowercase(), parts[1].replace("!important", "").trim(), "!important" in parts[1])
            }
            rule.groupValues[1].split(',').mapNotNull { CSS_SELECTOR_REGEX.matchEntire(it.trim()) }.flatMap { selector ->
                val (id, tag, classes) = selector.destructured
                properties.map { (name, value, important) ->
                    CssDeclaration(id, tag, classes.split('.').filter(String::isNotEmpty).toSet(), name, value, important, index)
                }
            }
        }.toList()
        return buildJsonObject {
            for ((id, rules) in declarations.groupBy { it.id }) {
                val element = page.getElementById(id) ?: continue
                putJsonObject(id) {
                    putJsonArray("children") {
                        for (child in element.children()) {
                            val classes = child.classNames()
                            val winners = rules.filter { (it.tag.isEmpty() || it.tag == child.tagName()) && classes.containsAll(it.classes) }
                                .groupBy { it.name }
                                .mapValues { (_, candidates) -> candidates.maxWith(compareBy({ it.important }, { it.specificity }, { it.index })).value }
                            addJsonObject { putJsonObject("style") { winners.forEach { (name, value) -> put(name, value) } } }
                        }
                    }
                }
            }
        }.toString()
    }

    private class CssDeclaration(
        val id: String,
        val tag: String,
        val classes: Set<String>,
        val name: String,
        val value: String,
        val important: Boolean,
        val index: Int,
    ) {
        // Every rule here starts with the same id, so the classes and the tag decide
        val specificity get() = classes.size * 10 + if (tag.isEmpty()) 0 else 1
    }

    companion object {
        private const val OUTDATED = "Información desactualizada. Refresque la lista de capítulos."
        private val CHAPTER_KEY_REGEX = Regex("""\d+(?:\.\d+)?""")
        private val TOTAL_PAGES_REGEX = Regex("""totalPages:\s*(\d+)""")
        private val AD_TICKET_REGEX = Regex("""adTicket\s*=\s*(\{[^}]*\})""")
        private val COUNTDOWN_REGEX = Regex("""let\s+countdown\s*=\s*(\d+)""")
        private val CSS_COMMENT_REGEX = Regex("""/\*[\s\S]*?\*/""")
        private val CSS_RULE_REGEX = Regex("""([^{}]+)\{([^{}]*)\}""")
        private val CSS_SELECTOR_REGEX = Regex("""#([\w-]+)\s*>?\s*([a-z]*)((?:\.[\w-]+)+)""")
    }
}
