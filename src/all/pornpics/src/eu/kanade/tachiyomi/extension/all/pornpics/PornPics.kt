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
        // the default list is always JSON,
        // the search list is always JSON,
        // the first page of other classification lists is HTML, and other pages are JSON
        val url = response.request.url
        val isSearch = url.queryParameter("q") != null
        val isDefault = url.queryParameter("period") != null
        val page = url.queryParameter("offset")!!.toInt()
        val responseAsJson = isSearch || isDefault || page > 0

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
            val doc = response.asJsoup()
            doc.select(simpleMangaSelector()).map {
                val imgEl = it.selectFirst("img")!!
                SManga.create().apply {
                    setUrlWithoutDomain(it.absUrl("href"))
                    title = imgEl.attr("alt")
                    thumbnail_url = imgEl.absUrl("data-src")
                }
            }
        }
        // response maybe [], Add +1 to requested image count per page,
        // Compare actual received count with pageSize to determine next page.
        val hasNextPage = mangas.size > QUERY_PAGE_SIZE
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
            .addQueryParameter("lang", intl.chosenLanguage)

        if (isDefaultCategory) {
            builder.addQueryParameter("category_id", 2585 + period)
                .addQueryParameter("period", period)
        }

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
        val activeCategoryTypeSelector = filters.firstInstance<PornPicsFilters.ActiveCategoryTypeSelector>()
        return if (query.isBlank() && activeCategoryTypeSelector.isSelected()) {
            buildCategoryRequest(page, activeCategoryTypeSelector, filters)
        } else {
            buildSearchRequest(page, query, filters)
        }
    }

    private fun buildCategoryRequest(
        page: Int,
        activeCategoryTypeSelector: PornPicsFilters.ActiveCategoryTypeSelector,
        filters: FilterList,
    ): Request {
        val categoryOption = activeCategoryTypeSelector.selectedCategoryOption(filters)
        val sortUrlPart = filters.firstInstance<PornPicsFilters.SortSelector>().toUriPart()
        val isSearch = categoryOption.toUrlPart().contains('?')
        val builder = baseUrl.toHttpUrl().newBuilder()
            .addUrlPart(categoryOption.toUrlPart())
            .addQueryParameter("lang", intl.chosenLanguage)
            .addUrlPart(sortUrlPart, addPath = !isSearch)
            .addQueryPageParameter(page)
        return GET(builder.build(), headers)
    }

    private fun buildSearchRequest(page: Int, query: String, filters: FilterList): Request {
        val sortOption = filters.firstInstance<PornPicsFilters.SortSelector>()
        val builder = "$baseUrl/search/srch.php".toHttpUrl().newBuilder()
            .addQueryParameter("lang", intl.chosenLanguage)
            .addUrlPart(sortOption.toUriPart(), addPath = false)
            .addQueryPageParameter(page)
            .addQueryParameter("q", query)
        return GET(builder.build(), headers)
    }

    override fun getFilterList() = FilterList(
        PornPicsFilters.createSortSelector(intl),
        Filter.Separator(),
        Filter.Header(intl["filter.header.ignored-when-search"]),
        Filter.Separator(),
        Filter.Header(intl["filter.header.select-active-category-type"]),
        PornPicsFilters.createActiveCategoryTypeSelector(intl),
        Filter.Separator(),
        Filter.Header(intl["filter.header.select-category-type-param"]),
        PornPicsFilters.createRecommendSelector(intl),
        PornPicsFilters.createCategorySelector(intl),
        PornPicsFilters.createTagSelector(intl),
        PornPicsFilters.createPornStarSelector(intl),
        PornPicsFilters.createChannelSelector(intl),
    )
}
