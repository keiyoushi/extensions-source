package eu.kanade.tachiyomi.extension.en.mangamirai

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

class MangaMirai :
    HttpSource(),
    ConfigurableSource {
    override val name = "Manga Mirai"
    override val baseUrl = "https://mangamirai.com"
    override val lang = "en"
    override val supportsLatest = true

    private val preferences by getPreferencesLazy()
    private val acceptHeaders = headersBuilder()
        .set("Accept", "*/*")
        .build()

    override val client = network.client.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/rankings".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div#test-ranking-card").map(::mangaFromElement)
        val hasNextPage = document.selectFirst("a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/sections/a025930c-41de-49f6-bd15-3b8de58962ab".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.card").map(::mangaFromElement)
        val hasNextPage = document.selectFirst("a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("word", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href").toHttpUrl().pathSegments.last())
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/product_collections/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            author = document.select("h1 ~ table a[href^=/authors/]").joinToString { it.text() }
            description = document.selectFirst("span[data-product-collections--product-collection--long-description-accordion-target]")?.text()
            genre = document.select("div.hidden > .popular-categories a").joinToString { it.text() }
            status = if (document.selectFirst(".popular-categories a[href*=/tags/Completed]") != null) SManga.COMPLETED else SManga.ONGOING
            thumbnail_url = document.selectFirst("div.grid-cols-5.justify-between img")?.absUrl("src")
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapters = mutableListOf<SChapter>()
        var page = 1

        do {
            val url = "$baseUrl/product_collections/${manga.url}".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .build()
            val response = client.newCall(GET(url, headers)).execute()
            val document = response.asJsoup()

            chapters += document.select("div.grid-cols-8.border-t").map {
                SChapter.create().apply {
                    setUrlWithoutDomain(it.selectFirst("a[href*=/book_reader]")!!.absUrl("href").toHttpUrl().pathSegments[2])
                    name = it.selectFirst("h3 > a")!!.text()
                }
            }
            page++
        } while (document.selectFirst("a[rel=next]") != null)

        return Observable.just(chapters.reversed())
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = "$baseUrl/users/product_contents/${chapter.url}/product_content_images".toHttpUrl().newBuilder()
            .addQueryParameter("start_page", "1")
            .addQueryParameter("limit", "10000")
            .build()
        return GET(url, acceptHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ViewerResponse>()
        return result.records.map {
            Page(it.page, imageUrl = "${it.url}#${it.scrambleKey}")
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
