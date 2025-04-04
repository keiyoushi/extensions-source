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
import eu.kanade.tachiyomi.source.model.UpdateStrategy
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
    internal data class MangaDto(
        val desc: String,
        @SerialName("g_url")
        val url: String,
        @SerialName("t_url")
        val thumbnailUrl: String,
    )

    override fun simpleMangaParse(response: Response): MangasPage {
        val url = response.request.url
        val parseType = url.queryParameter(PornPicsConstants.http.QUERY_PARSE_TYPE)
        val responseAsJson = PornPicsConstants.http.QUERY_PARSE_TYPE_JSON == parseType

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
                val imgEl = it.selectFirst("img")!!
                SManga.create().apply {
                    setUrlWithoutDomain(it.absUrl("href"))
                    title = imgEl.attr("alt")
                    thumbnail_url = imgEl.absUrl("data-src")
                }
            }
        }
        // response maybe [],Add +1 to requested image count per page,
        // Compare actual received count with pageSize to determine next page.
        val hasNextPage = mangas.size > PornPicsConstants.http.QUERY_PAGE_SIZE
        val readerMangas = if (hasNextPage) mangas.dropLast(1) else mangas
        return MangasPage(readerMangas, hasNextPage)
    }

    override fun simpleNextPageSelector(): String? = null

    override fun popularMangaRequest(page: Int) = buildMangasPageRequest(page, 1)

    override fun latestUpdatesRequest(page: Int) = buildMangasPageRequest(page, 2)

    private fun buildMangasPageRequest(page: Int, period: Int): Request {
        val categoryOption = PornPicsPreferences.getCategoryOption(getPreferences())
        val isDefaultCategory = PornPicsPreferences.DEFAULT_CATEGORY_OPTION == categoryOption

        val builder = when {
            isDefaultCategory -> "$baseUrl/popular/api/galleries/list"
            period == 1 -> "$baseUrl/$categoryOption"
            period == 2 -> "$baseUrl/$categoryOption/recent"
            else -> baseUrl
        }.toHttpUrl().newBuilder()
            .addQueryPageParameter(page)

        if (isDefaultCategory) {
            builder.addQueryParameter("category_id", 2585 + period)
                .addQueryParameter("period", period)
                .addQueryParameter("lang", "en")
        }

        // The default list is always JSON, the first page of other classification lists is HTML, and other pages are JSON
        when {
            categoryOption == PornPicsPreferences.DEFAULT_CATEGORY_OPTION -> PornPicsConstants.http.QUERY_PARSE_TYPE_JSON
            page > 1 -> PornPicsConstants.http.QUERY_PARSE_TYPE_JSON
            else -> PornPicsConstants.http.QUERY_PARSE_TYPE_DOCUMENT
        }.also { builder.addQueryParameter(PornPicsConstants.http.QUERY_PARSE_TYPE, it) }

        return GET(builder.build(), headers)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val thumbEl = document.selectFirst(simpleMangaSelector())!!
        val imgEl = thumbEl.selectFirst("img")!!
        val infoEl = document.selectFirst("div.gallery-info.to-gall-info")

        return SManga.create().apply {
            title = imgEl.attr("alt")
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            status = SManga.COMPLETED
            author = infoEl?.select("div.gallery-info__item:nth-child(2) a")?.joinToString { it.text() }
            genre = infoEl?.select("div.gallery-info__item:not(:nth-child(2)) a")?.joinToString { it.text() }
            description = infoEl?.selectFirst("div.gallery-info__item:nth-child(4)")?.text()
        }
    }

    override fun chapterListSelector() = "li.mobile a.alt-lang-item[data-lang=en]"
    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        chapter_number = 0F
        setUrlWithoutDomain(element.absUrl("href"))
        name = intl["chapter.name.default"]
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(simpleMangaSelector())
            .mapIndexed { index, element ->
                Page(index, imageUrl = element.absUrl("href"))
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchUrl = "$baseUrl/search/srch.php".toHttpUrl().newBuilder()
            .addQueryParameter(PornPicsConstants.http.QUERY_PARSE_TYPE, PornPicsConstants.http.QUERY_PARSE_TYPE_JSON)
            .addQueryParameter("lang", "en")
            .addQueryPageParameter(page)
            .addEncodedQueryParameter("q", query)

        filters.firstInstance<UriPartFilter>().toUriPart()?.let {
            searchUrl.addEncodedQueryParameter("date", it)
        }
        return GET(searchUrl.build(), headers)
    }

    override fun getFilterList() = FilterList(
        UriPartFilter(
            intl["filter.time.title"],
            arrayOf(
                Pair(intl["filter.time.option.popular"], null),
                Pair(intl["filter.time.option.recent"], "latest"),
            ),
        ),
    )

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String?>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private fun HttpUrl.Builder.addQueryParameter(encodedName: String, encodedValue: Int) =
        addQueryParameter(encodedName, encodedValue.toString())

    private fun HttpUrl.Builder.addQueryPageParameter(page: Int) =
    // Add +1 to requested image count per page,
        // Compare actual received count with pageSize to determine next page.
        this.addQueryParameter("limit", PornPicsConstants.http.QUERY_PAGE_SIZE + 1)
            .addQueryParameter("offset", (page - 1) * PornPicsConstants.http.QUERY_PAGE_SIZE)
}
