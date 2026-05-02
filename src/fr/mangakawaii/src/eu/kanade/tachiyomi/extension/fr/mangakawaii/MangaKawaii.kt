package eu.kanade.tachiyomi.extension.fr.mangakawaii

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class MangaKawaii : HttpSource() {

    override val name = "Mangakawaii"
    override val baseUrl = "https://www.mangakawaii.io"
    private val cdnUrl = "https://cdn2.mangakawaii.io"
    override val lang = "fr"
    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US)

    // Popular
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a.hot-manga__item").map { element ->
            SManga.create().apply {
                title = element.select("div.hot-manga__item-caption div.hot-manga__item-name").text()
                setUrlWithoutDomain(element.absUrl("href"))
                thumbnail_url = "$cdnUrl/uploads${element.attr("href")}/cover/cover_250x350.jpg"
            }
        }
        return MangasPage(mangas, false)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".section__list-group li div.section__list-group-left").map { element ->
            SManga.create().apply {
                val a = element.select("a")
                title = a.attr("title")
                setUrlWithoutDomain(a.attr("abs:href"))
                thumbnail_url = "$cdnUrl/uploads${a.attr("href")}/cover/cover_250x350.jpg"
            }
        }
        return MangasPage(mangas, false)
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("search_type", "manga")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.section__list-group-heading").map { element ->
            SManga.create().apply {
                val a = element.select("a")
                title = a.text()
                setUrlWithoutDomain(a.attr("abs:href"))
                thumbnail_url = "$cdnUrl/uploads${a.attr("href")}/cover/cover_250x350.jpg"
            }
        }
        val hasNextPage = document.select("ul.pagination a[rel*=next]").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    // Manga details
    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        thumbnail_url = document.select("div.manga-view__header-image img").attr("abs:src")
        description = document.select("dd.text-justify.text-break").text()
        author = document.select("a[href*=author]").text()
        artist = document.select("a[href*=artist]").text()
        genre = document.select("a[href*=category]").joinToString { it.text() }
        status = when (document.select("span.badge.bg-success.text-uppercase").text()) {
            "En Cours" -> SManga.ONGOING
            "Terminé" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        // add alternative name to manga description
        val altNames = document.select("span[itemprop=name alternativeHeadline]").joinToString { it.ownText() }
        if (altNames.isNotEmpty()) {
            description = buildString {
                if (!description.isNullOrBlank()) {
                    append(description)
                    append("\n\n")
                }
                append("Alternative Names: ")
                append(altNames)
            }
        }
    }

    // Chapter list
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val visibleChapters = document.select("tr[class*='volume-']")
        if (visibleChapters.isNotEmpty()) {
            val someChapter = visibleChapters.first()?.select(".table__chapter > a")?.attr("href")
            val notVisibleChapters = if (!someChapter.isNullOrEmpty()) {
                val mangaDocument = client.newCall(GET("$baseUrl$someChapter", headers)).execute().asJsoup()
                mangaDocument.select("#dropdownMenuOffset+ul li")
            } else {
                null
            }

            // If not everything is displayed
            if (notVisibleChapters != null && visibleChapters.size < notVisibleChapters.size) {
                return notVisibleChapters.map {
                    SChapter.create().apply {
                        setUrlWithoutDomain(it.select("a").attr("abs:href"))
                        name = it.select("a").text()
                    }
                }
            } else {
                return visibleChapters.map {
                    SChapter.create().apply {
                        setUrlWithoutDomain(it.select("td.table__chapter > a").attr("abs:href"))
                        name = it.select("td.table__chapter > a span").text()
                        date_upload = dateFormat.tryParse(it.select("td.table__date").text())
                    }
                }
            }
        }
        return emptyList()
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val documentString = document.toString()
        val chapterSlug = CHAPTER_SLUG_REGEX.find(documentString)?.groupValues?.get(1) ?: return emptyList()
        val mangaSlug = MANGA_SLUG_REGEX.find(documentString)?.groupValues?.get(1) ?: return emptyList()
        val appLocale = APP_LOCALE_REGEX.find(documentString)?.groupValues?.get(1) ?: "fr"
        val chapterServer = CHAPTER_SERVER_REGEX.find(documentString)?.groupValues?.get(1) ?: "cdn2"

        val pagesJson = PAGES_REGEX.find(documentString)?.groupValues?.get(1)
        if (pagesJson != null) {
            val cdn = "https://$chapterServer.mangakawaii.io"
            return pagesJson.parseAs<List<Dto>>().mapIndexed { i, dto ->
                Page(i, imageUrl = dto.getImageUrl(cdn, mangaSlug, appLocale, chapterSlug))
            }
        }

        // Fallback for older format if pages variable is not found
        val pages = mutableListOf<Page>()
        FALLBACK_PAGE_REGEX.findAll(documentString).forEachIndexed { i, it ->
            pages.add(
                Page(
                    i,
                    imageUrl = "$cdnUrl/uploads/manga/$mangaSlug/chapters_$appLocale/$chapterSlug/${it.groupValues[1]}",
                ),
            )
        }
        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Referer", baseUrl)
        }.build()
        return GET(page.imageUrl!!, imgHeaders)
    }

    companion object {
        private val CHAPTER_SLUG_REGEX = Regex("""var chapter_slug = "([^"]*)";""")
        private val MANGA_SLUG_REGEX = Regex("""var oeuvre_slug = "([^"]*)";""")
        private val APP_LOCALE_REGEX = Regex("""var applocale = "([^"]*)";""")
        private val CHAPTER_SERVER_REGEX = Regex("""var chapter_server = "([^"]*)";""")
        private val PAGES_REGEX = Regex("""var pages = (\[.*?]);""")
        private val FALLBACK_PAGE_REGEX = Regex(""""page_image":"([^"]*)"""")
    }
}
