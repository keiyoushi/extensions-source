package eu.kanade.tachiyomi.extension.ja.twi4

import android.app.Application
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable
import uy.kohesive.injekt.injectLazy

class Twi4 : HttpSource() {
    // The domain sai-zen-sen.jp directs to their main site rather than Twi4. It has to be /comics/twi4
    override val baseUrl: String = "https://sai-zen-sen.jp/comics/twi4/"
    override val lang: String = "ja"
    override val name: String = "Twi4"
    override val supportsLatest: Boolean = false
    private val application: Application by injectLazy()
    private val validPageTest: Regex =
        Regex("/comics/twi4/\\w+/works/\\d{4}\\.[0-9a-zA-Z]{32}\\.jpg")

    companion object Constants {
        const val SEARCH_PREFIX_SLUG = "SLUG:"
    }

    private fun getUrlDomain(): String = baseUrl.substring(0, 22)

    private fun getChromeHeaders(): Headers = headersBuilder().add(
        "User-Agent",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/95.0.4638.54 Safari/537.36",
    ).build()

    // Both latest and popular only lists 4 manga in total
    // As the full catalog is consists of less than 50 manga, it is not worth implementing
    // We'll just list all manga in the catalog instead
    override fun popularMangaParse(response: Response): MangasPage {
        val doc = Jsoup.parse(response.body.string())
        val ret = mutableListOf<SManga>()
        // Manga that are recently updated don't show up on the full catalog
        // So we'll need to parse the recent updates section as well
        val listings = arrayOf(
            "#lineup_recent > div> section",
            "#lineup > div > section:not(.zadankai):not([id])",
        )
        for (listing in listings) {
            val mangas = doc.select(listing)
            for (manga in mangas) {
                ret.add(
                    SManga.create().apply {
                        thumbnail_url =
                            getUrlDomain() + manga.select("div.figgroup > figure > a > img")
                                .attr("src")
                        setUrlWithoutDomain(
                            getUrlDomain() + manga.select("div.hgroup > h3 > a").attr("href"),
                        )
                        title = manga.select("div.hgroup > h3 > a").text()
                        author = manga.select("div.hgroup > p").text()
                        status =
                            if (manga.select("ul:first-child > li:last-child > em.is-completed")
                                    .isEmpty()
                            ) {
                                SManga.ONGOING
                            } else {
                                SManga.COMPLETED
                            }
                    },
                )
            }
        }
        return MangasPage(ret, false)
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, getChromeHeaders())
    }

    override fun mangaDetailsRequest(manga: SManga): Request =
        GET(getUrlDomain() + manga.url, getChromeHeaders())

    override fun mangaDetailsParse(response: Response): SManga {
        val document = Jsoup.parse(response.body.string())
        return SManga.create().apply {
            // We need to get the title and thumbnail again.
            // This is only needed if you search by slug, as we have no information about the them.
            // Interestingly the page body has no mention of the title at all. It only exists in <title>
            val titleRegex = Regex("『(.+)』.+ \\| ツイ４ \\| 最前線")
            val match = titleRegex.matchEntire(document.title())
            title = match?.groups?.get(1)?.value.toString()
            // Twi4 uses the exact same thumbnail at both the main page and manga details
            thumbnail_url =
                getUrlDomain() + document.select("#introduction > header > div > h2 > img")
                    .attr("src")
            description =
                document.select("#introduction > div > div > p").text()
            // Determine who are the authors and artists
            // 作者, 原作 -> Author (Also the artist) / Original author (Such as light novel adaptation)
            // 漫画 -> Artist only
            // 提供, etc, etc -> Sponsors, irrelevant stuff
            val staffs = document.select("#introduction > div > section > header > div > h3")
            for (staff in staffs) {
                val role = staff.select("small")
                if (role.isEmpty()) {
                    continue
                }
                when (role.text().replace("：", "").trim()) {
                    "作者" -> {
                        author = staff.select("span").text()
                        artist = staff.select("span").text()
                    }
                    // If 作者 and 原作 appear at the same time, 原作 will overwrite the author field
                    "原作" -> {
                        author = staff.select("span").text()
                    }
                    "漫画" -> {
                        artist = staff.select("span").text()
                    }
                }
            }
            // While the status can be obtained at the home page, there is no such info at the details page
        }
    }

    override fun chapterListRequest(manga: SManga): Request =
        GET(getUrlDomain() + manga.url, getChromeHeaders())

    // They have a <noscript> layout! This is surprising
    // Though their manga pages fails to load as it relies on JS
    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = Jsoup.parse(response.body.string())
        val chapterRegex = Regex(".+『(.+)』 #(\\d+)")
        val allChapters = doc.select("#backnumbers > div > ul > li")
        val ret = mutableListOf<SChapter>()
        // Returns the shortened series name from the request URL. Should be good enough for an identifier
        val series = response.request.url.toUrl().toString().dropLast(1).substringAfterLast("/")
        val sharedPref = application.getSharedPreferences("source_${id}_time_found:$series", 0)
        val editor = sharedPref.edit()
        for (chapter in allChapters) {
            val match = chapterRegex.matchEntire(chapter.select("a").text())
            val chapNumber = match?.groups?.get(2)?.value
            ret.add(
                SChapter.create().apply {
                    if (chapNumber != null) {
                        this.chapter_number = chapNumber.toFloat()
                        this.name = chapNumber.toInt().toString() + " - " + match.groups[1]?.value
                    }
                    setUrlWithoutDomain(chapter.select("a").attr("href"))
                    // We can't determine the upload date from the website
                    // Store date_upload when a chapter is found for the first time
                    // *Borrowed from CatManga
                    val dateFound = System.currentTimeMillis()
                    if (!sharedPref.contains(chapNumber)) {
                        editor.putLong(chapNumber, dateFound)
                    }
                    this.date_upload = sharedPref.getLong(chapNumber, dateFound)
                },
            )
        }
        editor.apply()
        ret.sortByDescending { chapter -> chapter.chapter_number }
        return ret
    }

    override fun pageListRequest(chapter: SChapter): Request =
        GET(getUrlDomain() + chapter.url, getChromeHeaders())

    override fun pageListParse(response: Response): List<Page> {
        val doc = Jsoup.parse(response.body.string())
        // The site interprets 1 page == 1 chapter
        // There should only be 1 article in the document
        val page = doc.select("article.comic:first-child")
        val ret = mutableListOf<Page>()
        // The image url *in most cases* supposed to look like this /comic/twi4/comicName/works/pageNumber.suffix.jpg
        // The noscript page broke the image links in a few mangas and they don't come with the suffix
        // In this case we need to request an index file and obtain the file suffix
        var imageUrl: String = getUrlDomain() + page.select("div > div > p > img").attr("src")
        if (!validPageTest.matches(page.select("div > div > p > img").attr("src"))) {
            val requestUrl = response.request.url.toUrl().toString()
            val chapterNum = requestUrl.substringAfterLast("/").take(4).toInt()
            // The index file contains everything about each image. Usually we can find the file name directly from the document
            // This is a failsafe
            val indexResponse = client.newCall(
                GET(
                    requestUrl.substringBeforeLast("/") + "/index.js",
                    getChromeHeaders(),
                ),
            ).execute()
            if (!indexResponse.isSuccessful) {
                throw Exception("Failed to find pages!")
            }
            // We got a JS file that looks very much like a JSON object
            // A few string manipulation and we can parse the whole thing as JSON!
            val re = Regex("([A-z]+):")
            val index = indexResponse.body.string().substringAfter("=").dropLast(1)
                .let { re.replace(it, "\"$1\":") }
            indexResponse.close()
            val indexElement = index.let { Json.parseToJsonElement(it) }
            val suffix =
                indexElement.jsonObject["Items"]?.jsonArray?.get(chapterNum - 1)?.jsonObject?.get("Suffix")?.jsonPrimitive?.content
            // Twi4's image links are a bit of a mess
            // Because in very rare cases, the image filename *doesn't* come with a suffix
            // So only attach the suffix if there is one
            if (suffix != null) {
                imageUrl = getUrlDomain() + page.select("div > div > p > img").attr("src")
                    .dropLast(4) + suffix + ".jpg"
            }
        }
        ret.add(
            Page(
                index = page.select("header > div > h3 > span.number").text().toInt(),
                imageUrl = imageUrl,
            ),
        )
        return ret
    }

    // There is no search functionality in the site
    // It is possible to implement something rudimentary for search to function
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if (query.startsWith(SEARCH_PREFIX_SLUG)) {
            val slug = query.drop(SEARCH_PREFIX_SLUG.length)
            // Explicitly ignore anything that ends with .html or starts with zadankai
            // These will include the completed manga page, about page and zadankai submissions
            // For reasons to exclude zadankai, see parsePopularMangaRequest()

            // There will still be some urls that would accidentally activate the intent (like the news page),
            // but there's no way to avoid it.
            if (slug.endsWith("html") || slug.startsWith("zadankai") || slug.startsWith("others")) {
                return Observable.just(MangasPage(listOf(), false))
            }
            return client.newCall(GET(baseUrl + slug))
                .asObservableSuccess()
                .map { response -> searchMangaSlug(response, slug) }
        }
        return fetchPopularManga(page).map { mp ->
            mp.copy(
                mp.mangas.filter {
                    it.title.contains(query, true)
                },
            )
        }
    }

    private fun searchMangaSlug(response: Response, slug: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.setUrlWithoutDomain(baseUrl + slug)
        return MangasPage(listOf(details), false)
    }

    // All these functions are unused
    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used")

    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not used")

    override fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw UnsupportedOperationException("Not used")
}
