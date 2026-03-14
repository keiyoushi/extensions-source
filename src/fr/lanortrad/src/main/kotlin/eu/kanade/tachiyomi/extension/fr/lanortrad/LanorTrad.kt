package eu.kanade.tachiyomi.extension.fr.lanortrad

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable
import uy.kohesive.injekt.injectLazy

class LanorTrad : HttpSource() {

    override val name = "LanorTrad"
    override val baseUrl = "https://lanortrad.netlify.app"
    override val lang = "fr"
    override val supportsLatest = false

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/js/utile/mangaData.js", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = parseMangaData(response.body.string()).map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(page)

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = client.newCall(searchMangaRequest(page, query, filters)).asObservableSuccess()
        .map { response ->
            // Source stores all manga metadata in a single JS file
            val allMangas = parseMangaData(response.body.string())
            val filtered = allMangas.filter { it.title.contains(query, ignoreCase = true) }
                .map { it.toSManga() }
            MangasPage(filtered, false)
        }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = client.newCall(GET("$baseUrl/js/utile/mangaData.js", headers)).asObservableSuccess()
        .map { response ->
            val mangaList = parseMangaData(response.body.string())
            val mangaData = mangaList.find { "/Manga/${it.id}.html".replace(" ", "%20") == manga.url } ?: throw Exception("Manga not found")
            mangaData.toSManga().apply {
                url = manga.url
            }
        }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)
    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(GET(baseUrl + manga.url, headers)).asObservableSuccess()
        .flatMap { response ->
            val document = response.asJsoup()

            // Oneshots links directly to a dedicated HTML reader page
            document.selectFirst("a[href*=neshot]")?.let { oneshotElement ->
                val relativeUrl = oneshotElement.absUrl("href").removePrefix(baseUrl).replace(" ", "%20").let {
                    if (it.startsWith("/")) it else "/$it"
                }

                return@flatMap Observable.just(
                    listOf(
                        SChapter.create().apply {
                            url = relativeUrl
                            name = "Oneshot"
                            chapter_number = 1f
                        },
                    ),
                )
            }

            document.selectFirst("script[src*=/js/manga/]")?.let { scriptElem ->
                val scriptUrl = scriptElem.absUrl("src")
                client.newCall(GET(scriptUrl, headers)).asObservableSuccess()
                    .map { response ->
                        parseChaptersJs(response.body.string())
                    }
            } ?: Observable.just(emptyList())
        }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    private fun parseChaptersJs(js: String): List<SChapter> {
        val maxChapters = Regex("""maxChapters:\s*(\d+)""").find(js)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val currentManga = Regex("""currentManga:\s*['"]([^'"]+)['"]""").find(js)?.groupValues?.get(1) ?: ""
        val chapterPrefix = Regex("""chapterPrefix:\s*['"]([^'"]+)['"]""").find(js)?.groupValues?.get(1) ?: "Chapitre"

        val chapters = mutableListOf<SChapter>()

        // Regular chapters
        for (i in 1..maxChapters) {
            chapters.add(
                SChapter.create().apply {
                    name = "$chapterPrefix $i"
                    url = "/Manga/$currentManga/$chapterPrefix $i.html"
                    chapter_number = i.toFloat()
                },
            )
        }

        // Bonus chapters (numbered with decimals)
        Regex("""bonusChapters\s*=\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(js)?.groupValues?.get(1)?.let { bonusBlock ->
            val bonusRegex = Regex("""number:\s*([\d.]+)""")
            for (match in bonusRegex.findAll(bonusBlock)) {
                val numStr = match.groupValues[1]
                chapters.add(
                    SChapter.create().apply {
                        name = "$chapterPrefix $numStr"
                        url = "/Manga/$currentManga/$chapterPrefix $numStr.html"
                        chapter_number = numStr.toFloatOrNull() ?: -1f
                    },
                )
            }
        }

        return chapters.distinctBy { it.url }.sortedByDescending { it.chapter_number }
    }

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()
        val pages = mutableListOf<Page>()

        val afficheRegex = Regex("""firstImg\.src\s*=\s*['"]([^'"]+)['"]""")
        afficheRegex.find(body)?.let { match ->
            pages.add(Page(pages.size, "", match.groupValues[1]))
        }

        val loopRegex = Regex("""for\s*\([^;]+;\s*[a-zA-Z]+\s*<=\s*(\d+)[\s;]""")
        val pathRegex = Regex("""imgElement\.src\s*=\s*`([^$]+)\$\{""")
        val extRegex = Regex("""\}\.([^`]+)`""")
        val padRegex = Regex("""padStart\((\d+)""")

        val maxPagesMatch = loopRegex.find(body)
        if (maxPagesMatch != null) {
            val maxPages = maxPagesMatch.groupValues[1].toInt()
            val pathPrefix = pathRegex.find(body)?.groupValues?.get(1) ?: ""
            val pathExt = extRegex.find(body)?.groupValues?.get(1) ?: "jpg"
            val pad = padRegex.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 3

            for (i in 1..maxPages) {
                val num = i.toString().padStart(pad, '0')
                pages.add(Page(pages.size, "", "$pathPrefix$num.$pathExt"))
            }
        }

        // Fallback for Oneshots
        if (pages.isEmpty()) {
            val document = Jsoup.parse(body, response.request.url.toString())
            document.select("img").forEach { element ->
                val src = element.absUrl("src")
                if (src.isNotEmpty() && !src.contains("Logo") && !src.contains("postimg")) {
                    pages.add(Page(pages.size, "", src))
                }
            }
        }

        // Append recruitment image at the end
        val recvRegex = Regex("""lastImg\.src\s*=\s*['"]([^'"]+)['"]""")
        recvRegex.find(body)?.let { match ->
            pages.add(Page(pages.size, "", match.groupValues[1]))
        }

        pages.forEach { page ->
            page.imageUrl = response.request.url.resolve(page.imageUrl ?: "")?.toString() ?: page.imageUrl
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun parseMangaData(jsString: String): List<LanorMangaDto> {
        val jsonString = jsString.substringAfter("window.MANGA_DATA =").substringBeforeLast(";")
        if (jsonString.isBlank() || !jsonString.contains("[")) return emptyList()

        val fixedJson = jsonString
            // Remove double slashes that are not part of URLs
            .replace(Regex("""^\s*//.*$""", RegexOption.MULTILINE), "")
            // Wrap unquoted javascript object keys in double quotes
            .replace(Regex("""^(\s*)([a-zA-Z0-9_]+)\s*:""", RegexOption.MULTILINE)) { match ->
                "${match.groupValues[1]}\"${match.groupValues[2]}\":"
            }

        return runCatching {
            json.decodeFromString<List<LanorMangaDto>>(fixedJson)
        }.getOrElse { emptyList() }
    }

    private fun LanorMangaDto.toSManga() = SManga.create().also { manga ->
        manga.title = title
        val imgToUse = if (type.equals("oneshot", true)) image else coverImage
        manga.thumbnail_url = if (imgToUse.startsWith("http")) imgToUse else "$baseUrl/${imgToUse.removePrefix("/").replace(" ", "%20")}"
        manga.url = "/Manga/$id.html".replace(" ", "%20")
        manga.description = description
        manga.status = when (status.lowercase()) {
            "en cours" -> SManga.ONGOING
            "terminé" -> SManga.COMPLETED
            "en pause" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        manga.genre = genres.joinToString { it.trim() }
        manga.author = "LanorTrad"
    }
}
