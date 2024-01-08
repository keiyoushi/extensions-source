package eu.kanade.tachiyomi.extension.en.rmanga

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Rmanga : ConfigurableSource, ParsedHttpSource() {

    override val name = "Rmanga.app"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(4)
        .build()

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    override val baseUrl = preferences.getString(DOMAIN_PREF, "https://rmanga.app")!!

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/ranking/most-viewed/$page", headers)
    }

    override fun popularMangaSelector() = "div.category-items > ul > li"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("div.category-name a").let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.text()
            }
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector() = "a.pagination__item:contains(»)"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest-updates/$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return super.latestUpdatesParse(response).apply {
            this.mangas.distinctBy { it.url }
        }
    }

    override fun latestUpdatesSelector() = "div.latest-updates > ul > li"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("div.latest-updates-name a").let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.text()
            }
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val payload = FormBody.Builder().apply {
            add("manga-name", query.trim())
            filters.forEach { filter ->
                when (filter) {
                    is TypeFilter -> {
                        add("type", filter.getValue())
                    }
                    is AuthorFilter -> {
                        add("author-name", filter.state.trim())
                    }
                    is ArtistFilter -> {
                        add("artist-name", filter.state.trim())
                    }
                    is StatusFilter -> {
                        add("status", filter.getValue())
                    }
                    is GenreFilter -> {
                        filter.state.forEach { genreState ->
                            when (genreState.state) {
                                Filter.TriState.STATE_INCLUDE -> add("include[]", genreState.id)
                                Filter.TriState.STATE_EXCLUDE -> add("exclude[]", genreState.id)
                            }
                        }
                    }
                    else -> {}
                }
            }
        }.build()

        return POST("$baseUrl/detailed-search", headers, payload)
    }

    override fun getFilterList() = getFilters()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("div.section-header-title").first()!!.text()
            description = document.select("div.empty-box").eachText().joinToString("\n\n", postfix = "\n\n")
            thumbnail_url = document.select("div.novels-detail-left img").attr("abs:src")
            document.select("div.novels-detail-right > ul").let { element ->
                author = element.select("li:contains(author)").text().substringAfter(":").trim().takeUnless { it == "N/A" }
                artist = element.select("li:contains(artist)").text().substringAfter(":").trim().takeUnless { it == "N/A" }
                genre = element.select("li:contains(genres) a").joinToString { it.text() }
                status = element.select("li:contains(status)").text().parseStatus()
                description += element.select("li:contains(alternative)").text()
            }
        }
    }

    private fun String.parseStatus(): Int {
        return when {
            this.contains("ongoing", true) -> SManga.ONGOING
            this.contains("completed", true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    override fun chapterListSelector() = "div.novels-detail-chapters a"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            name = element.ownText()
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.chapter-detail-novel-big-image img").mapIndexed { index, img ->
            Page(index = index, imageUrl = img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("Not Used")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = DOMAIN_PREF
            title = "Preferred domain"
            entries = arrayOf("rmanga.app", "readmanga.app")
            entryValues = arrayOf("https://rmanga.app", "https://readmanga.app")
            setDefaultValue("https://rmanga.app")
            summary = "Requires App Restart"
        }.let { screen.addPreference(it) }
    }

    companion object {
        private const val DOMAIN_PREF = "pref_domain"
    }
}
