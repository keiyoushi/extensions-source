package eu.kanade.tachiyomi.extension.es.mangasnosekai

import eu.kanade.tachiyomi.lib.synchrony.Deobfuscator
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangasNoSekai : Madara(
    "Mangas No Sekai",
    "https://mangasnosekai.com",
    "es",
    SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2, 1)
        .build()

    override val useNewChapterEndpoint = true

    private var libraryPath = ""

    private fun getLibraryPath() {
        libraryPath = try {
            val document = client.newCall(GET(baseUrl, headers)).execute().asJsoup()
            val libraryUrl = document.selectFirst("ul > li[id^=menu-item] > a[href]")

            libraryUrl?.attr("href")?.removeSuffix("/")?.substringAfterLast("/")
                ?: "manganews3"
        } catch (e: Exception) {
            "manganews3"
        }
    }

    override fun popularMangaRequest(page: Int): Request {
        if (libraryPath.isBlank()) getLibraryPath()
        return GET("$baseUrl/$libraryPath/#$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val page = response.request.url.fragment?.toIntOrNull() ?: 1
        val document = response.asJsoup()
        val orderValue = document.selectFirst("select#order_select > option:eq(5)")
            ?.attr("value")
            ?: "views2"
        val url = "$baseUrl/$libraryPath/${searchPage(page)}?m_orderby=$orderValue"
        val newResponse = client.newCall(GET(url, headers)).execute()
        return super.popularMangaParse(newResponse)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        if (libraryPath.isBlank()) getLibraryPath()
        return GET("$baseUrl/$libraryPath/#$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val page = response.request.url.fragment?.toIntOrNull() ?: 1
        val document = response.asJsoup()
        val orderValue = document.selectFirst("select#order_select > option:eq(1)")
            ?.attr("value")
            ?: "latest2"
        val url = "$baseUrl/$libraryPath/${searchPage(page)}?m_orderby=$orderValue"
        val newResponse = client.newCall(GET(url, headers)).execute()
        return super.popularMangaParse(newResponse)
    }

    override fun popularMangaSelector() = "div.page-listing-item > div.row > div"

    override fun popularMangaNextPageSelector() = "a.next.page-numbers"

    override val popularMangaUrlSelector = "a[href]"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        with(element) {
            selectFirst(popularMangaUrlSelector)!!.let {
                manga.setUrlWithoutDomain(it.attr("abs:href"))
            }

            selectFirst("figcaption")!!.let {
                manga.title = it.text()
            }

            selectFirst("img")?.let {
                manga.thumbnail_url = imageFromElement(it)
            }
        }

        return manga
    }

    override fun searchMangaNextPageSelector() = "nav.navigation a.next"

    override val mangaDetailsSelectorTitle = "div.thumble-container p.titleMangaSingle"
    override val mangaDetailsSelectorThumbnail = "div.thumble-container img.img-responsive"
    override val mangaDetailsSelectorDescription = "section#section-sinopsis > p"
    override val mangaDetailsSelectorStatus = "section#section-sinopsis div.d-flex:has(div:contains(Estado)) p"
    override val mangaDetailsSelectorAuthor = "section#section-sinopsis div.d-flex:has(div:contains(Autor)) p a"
    override val mangaDetailsSelectorGenre = "section#section-sinopsis div.d-flex:has(div:contains(Generos)) p a"
    override val altNameSelector = "section#section-sinopsis div.d-flex:has(div:contains(Otros nombres)) p"
    override val altName = "Otros nombres: "

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        with(document) {
            selectFirst(mangaDetailsSelectorTitle)?.let {
                manga.title = it.ownText()
            }
            select(mangaDetailsSelectorAuthor).joinToString { it.text() }.let {
                manga.author = it
            }
            select(mangaDetailsSelectorDescription).let {
                manga.description = it.text()
            }
            select(mangaDetailsSelectorThumbnail).first()?.let {
                manga.thumbnail_url = imageFromElement(it)
            }
            selectFirst(mangaDetailsSelectorStatus)?.ownText()?.let {
                manga.status = when (it) {
                    in completedStatusList -> SManga.COMPLETED
                    in ongoingStatusList -> SManga.ONGOING
                    in hiatusStatusList -> SManga.ON_HIATUS
                    in canceledStatusList -> SManga.CANCELLED
                    else -> SManga.UNKNOWN
                }
            }
            val genres = select(mangaDetailsSelectorGenre)
                .map { element -> element.text().lowercase(Locale.ROOT) }
                .toMutableSet()

            manga.genre = genres.toList().joinToString(", ") { genre ->
                genre.replaceFirstChar {
                    if (it.isLowerCase()) {
                        it.titlecase(
                            Locale.ROOT,
                        )
                    } else {
                        it.toString()
                    }
                }
            }

            document.select(altNameSelector).firstOrNull()?.ownText()?.let {
                if (it.isBlank().not() && it.notUpdating()) {
                    manga.description = when {
                        manga.description.isNullOrBlank() -> altName + it
                        else -> manga.description + "\n\n$altName" + it
                    }
                }
            }
        }

        return manga
    }

    override val orderByFilterOptions: Map<String, String> = mapOf(
        intl["order_by_filter_relevance"] to "",
        intl["order_by_filter_latest"] to "latest2",
        intl["order_by_filter_az"] to "alphabet",
        intl["order_by_filter_rating"] to "rating",
        intl["order_by_filter_trending"] to "trending",
        intl["order_by_filter_views"] to "views2",
        intl["order_by_filter_new"] to "new-manga",
    )

    private fun altChapterRequest(url: String, mangaId: String, page: Int, objects: List<Pair<String, String>>): Request {
        val form = FormBody.Builder()
            .add("mangaid", mangaId)
            .add("page", page.toString())

        objects.forEach { (key, value) ->
            form.add(key, value)
        }

        return POST(baseUrl + url, xhrHeaders, form.build())
    }

    private val altChapterListSelector = "body > div > div"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        launchIO { countViews(document) }

        val txtUrl = "https://raw.githubusercontent.com/bapeey/extensions-tools/main/keiyoushi/mns/values.txt"
        val values = client.newCall(GET(txtUrl)).execute().body.string().split("\n")

        val mangaSlug = response.request.url.toString().substringAfter(baseUrl).removeSuffix("/")
        val coreScript = document.selectFirst(values[0])!!.attr("abs:src")
        val coreScriptBody = Deobfuscator.deobfuscateScript(client.newCall(GET(coreScript, headers)).execute().body.string())
            ?: throw Exception("No se pudo deobfuscar el script")

        val url = values[5].toRegex().find(coreScriptBody)?.groupValues?.get(1)
            ?: throw Exception("No se pudo obtener la url del capítulo")

        val data = values[5].toRegex().find(coreScriptBody)?.groupValues?.get(2)?.trim()
            ?: throw Exception("No se pudo obtener la data del capítulo")

        val objects = values[6].toRegex().findAll(data)
            .mapNotNull { matchResult ->
                val key = matchResult.groupValues[1]
                val value = matchResult.groupValues.getOrNull(2)
                if (!value.isNullOrEmpty()) key to value else null
            }.toList()

        val mangaId = document.selectFirst(values[1])?.data()
            ?.let { values[7].toRegex().find(it)?.groupValues?.get(1) }
            ?: document.selectFirst(values[2])?.data()
                ?.let { values[8].toRegex().find(it)?.groupValues?.get(1) }
            ?: throw Exception("No se pudo obtener el id del manga")

        val chapterElements = mutableListOf<Element>()
        var page = 1
        do {
            val xhrRequest = altChapterRequest(url, mangaId, page, objects)
            val xhrResponse = client.newCall(xhrRequest).execute()
            val xhrBody = xhrResponse.body.string()
            if (xhrBody.startsWith("{")) {
                return chaptersFromJson(xhrBody, mangaSlug)
            }
            val xhrDocument = Jsoup.parse(xhrBody)
            chapterElements.addAll(xhrDocument.select(altChapterListSelector))
            page++
        } while (xhrDocument.select(altChapterListSelector).isNotEmpty())

        return chapterElements.map(::altChapterFromElement)
    }

    private fun altChapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
        name = element.select("div.text-sm").text()
        date_upload = element.selectFirst("time")?.text()?.let {
            parseChapterDate(it)
        } ?: 0
    }

    private fun chaptersFromJson(jsonString: String, mangaSlug: String): List<SChapter> {
        val result = json.decodeFromString<PayloadDto>(jsonString)
        return result.manga.first().chapters.map { it.toSChapter(mangaSlug) }
    }
}
