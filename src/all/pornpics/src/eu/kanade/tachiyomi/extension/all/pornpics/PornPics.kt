package eu.kanade.tachiyomi.extension.all.pornpics

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class PornPics() : SimpleParsedHttpSource(), ConfigurableSource {

    override val baseUrl = "https://www.pornpics.com"
    override val lang = "all"
    override val name = "PornPics"
    override val supportsLatest = true

    private val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "zh"),
        classLoader = this::class.java.classLoader!!,
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        PornPicsPreferences.buildPreferences(screen.context, intl).forEach(screen::addPreference)
    }

    override fun simpleMangaSelector() = "#main li.thumbwook > a.rel-link"

    override fun simpleMangaFromElement(element: Element) = throw UnsupportedOperationException()

    @Serializable
    class MangaDto(
        val desc: String,
        @SerialName("g_url")
        val url: String,
        @SerialName("t_url")
        val thumbnailUrl: String,
    )

    override fun simpleMangaParse(response: Response): MangasPage {
        val requestContentType = response.request.header(PornPicsConstants.http.HEADER_CONTENT_TYPE)
        val responseAsJson = PornPicsConstants.http.HEADER_APPLICATION_JSON == requestContentType

        val mangas = if (responseAsJson) {
            val data = response.parseAs<List<MangaDto>>()
            data.map {
                SManga.create().apply {
                    setUrlWithoutDomain(it.url)
                    title = it.desc
                    thumbnail_url = it.thumbnailUrl
                }
            }
        } else {
            val doc = response.asJsoup().select(simpleMangaSelector())
            doc.map {
                SManga.create().apply {
                    val imgEl = it.selectFirst("img")!!
                    setUrlWithoutDomain(it.absUrl("href"))
                    title = imgEl.attr("alt")
                    thumbnail_url = imgEl.absUrl("data-src")
                }
            }
        }
        return MangasPage(mangas, mangas.size == PornPicsConstants.http.QUERY_PAGE_SIZE)
    }

    override fun simpleNextPageSelector(): String? = null

    override fun popularMangaRequest(page: Int) = buildMangasPageRequest(page, 1)

    override fun latestUpdatesRequest(page: Int) = buildMangasPageRequest(page, 2)

    /**
     * period:
     *  popular  : 1
     *  recent   : 2
     *  rating   : 3
     *  likes    : 4
     *  views    : 5
     *  comments : 6
     *
     * category_id
     *  2585 + period
     */
    private fun buildMangasPageRequest(page: Int, period: Int): Request {
        val builder = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("limit", PornPicsConstants.http.QUERY_PAGE_SIZE)
            .addQueryParameter("offset", (page - 1) * PornPicsConstants.http.QUERY_PAGE_SIZE)

        val categoryOption = PornPicsPreferences.getCategoryOption(getPreferences())
        when {
            categoryOption == PornPicsPreferences.DEFAULT_CATEGORY_OPTION ->
                builder
                    .addPathSegment("/popular/api/galleries/list")
                    .addQueryParameter("category_id", 2585 + period)
                    .addQueryParameter("period", period)
                    .addEncodedQueryParameter("lang", "en")

            period == 1 -> builder.addPathSegment("/$categoryOption")
            period == 2 -> builder.addPathSegment("/$categoryOption/recent")
        }

        // The default list is always JSON, the first page of other classification lists is HTML, and other pages are JSON
        val contentType = when {
            categoryOption == PornPicsPreferences.DEFAULT_CATEGORY_OPTION ||
                page > 1 -> PornPicsConstants.http.HEADER_APPLICATION_JSON

            else -> PornPicsConstants.http.HEADER_HTML_TEXT
        }

        val newHeaders = headers.newBuilder()
            .add(PornPicsConstants.http.HEADER_CONTENT_TYPE, contentType)
            .build()
        return GET(builder.build(), newHeaders)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val thumbEl = document.selectFirst(simpleMangaSelector())!!
        val imgEl = thumbEl.selectFirst("img")!!
        return SManga.create().apply {
            title = imgEl.attr("alt")
            genre = document.select(".gallery-info__item a").joinToString { it.text() }
        }
    }

    override fun chapterListSelector() = "li.mobile a.alt-lang-item[data-lang=en]"
    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        chapter_number = 0F
        setUrlWithoutDomain(element.absUrl("href"))
        name = "Gallery"
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(simpleMangaSelector())
            .mapIndexed { index, element ->
                Page(index, imageUrl = element.absUrl("href"))
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchUrl = baseUrl.toHttpUrl().newBuilder()
            .addEncodedQueryParameter("q", query)

        filters.firstInstance<UriPartFilter>().toUriPart()?.let {
            searchUrl.addEncodedQueryParameter("date", it)
        }
        return GET(searchUrl.build(), headers)
    }

    override fun getFilterList() = FilterList(
        UriPartFilter(
            "sort",
            arrayOf(
                Pair("Most Popular", null),
                Pair("Most Recent", "latest"),
            ),
        ),
    )

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String?>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private fun HttpUrl.Builder.addQueryParameter(encodedName: String, encodedValue: Int) =
        addQueryParameter(encodedName, encodedValue.toString())
}
