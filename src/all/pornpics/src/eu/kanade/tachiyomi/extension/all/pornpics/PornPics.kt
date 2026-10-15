package eu.kanade.tachiyomi.extension.all.pornpics

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class PornPics :
    KeiSource(),
    ConfigurableSource {

    private val preferences = getPreferences()

    override fun OkHttpClient.Builder.configureClient() = addCookie("pp_lang" to "")

    val language: String
        get() = when (lang) {
            "ja" -> "jp"
            else -> lang
        }

    private val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "zh"),
        classLoader = this::class.java.classLoader!!,
    )

    // Popular
    override suspend fun getPopularManga(page: Int) = getMangasPage(page, popular = true)

    // Latest
    override suspend fun getLatestUpdates(page: Int) = getMangasPage(page, popular = false)

    // MangasPage
    private val mangaSelector = "#main li.thumbwook > a.rel-link"

    private fun parseMangasPage(response: Response): MangasPage {
        val url = response.request.url
        val isSearch = url.queryParameter("q") != null
        val isDefault = url.queryParameter("period") != null
        val offset = url.queryParameter("offset")!!.toInt()
        val responseAsJson = isSearch || isDefault || offset > 0

        val mangas = if (responseAsJson) {
            response.parseAs<List<MangaDto>>().map {
                SManga.create().apply {
                    setUrlWithoutDomain(it.url)
                    title = it.title
                    thumbnail_url = it.thumbnailUrl
                }
            }
        } else {
            response.asJsoup().select(mangaSelector).map {
                val imgEl = it.selectFirst("img")!!
                SManga.create().apply {
                    setUrlWithoutDomain(it.absUrl("href"))
                    title = imgEl.attr("alt")
                    thumbnail_url = imgEl.absUrl("data-src")
                }
            }
        }
        // response may be []. Add +1 to requested image count per page;
        // compare actual received count with pageSize to determine next page.
        val hasNextPage = mangas.size > QUERY_PAGE_SIZE
        val readerMangas = if (hasNextPage) mangas.dropLast(1) else mangas
        return MangasPage(readerMangas, hasNextPage)
    }

    private suspend fun getMangasPage(page: Int, popular: Boolean): MangasPage {
        val categoryOption = Preferences.getCategoryOption(preferences)
        val url = if (Preferences.DEFAULT_CATEGORY_OPTION == categoryOption) {
            // the source of is the options under the pics menu in the nav bar
            val period = if (popular) 1 else 2
            val categoryId = 2585 + period
            "$baseUrl/popular/api/galleries/list/".toHttpUrl().newBuilder()
                .addQueryParameterPage(page)
                .addQueryParameter("lang", language)
                .addQueryParameter("period", period)
                .addQueryParameter("category_id", categoryId)
                .build()
        } else {
            // the source is the options under the categories/tags/pornstars/channels menu in the nav bar
            val requestBaseUrl = if (popular) "$baseUrl/$categoryOption/" else "$baseUrl/$categoryOption/recent/"
            requestBaseUrl.toHttpUrl().newBuilder()
                .addQueryParameterPage(page)
                .addQueryParameter("lang", language)
                .build()
        }
        return parseMangasPage(client.get(url))
    }

    // Search
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host ||
            url.pathSegments.count(String::isNotBlank) < 2
        ) {
            return null
        }
        return fetchMangaUpdate(
            SManga.create().apply { this.url = url.encodedPath },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isBlank()) {
            val activeCategoryTypeOption = filters.firstInstance<ActiveCategoryTypeSelector>()
            val categoryOption = activeCategoryTypeOption.selectedCategoryOption(filters)
            val sortOption = filters.firstInstance<SortSelector>()
            baseUrl.toHttpUrl().newBuilder().apply {
                addUrlPart(categoryOption.toUrlPart())
                addQueryParameter("lang", language)
                addUrlPart(sortOption.toUriPart(), addPath = !categoryOption.useSearch())
                addQueryParameterPage(page)
            }.build()
        } else {
            val sortOption = filters.firstInstance<SortSelector>()
            "$baseUrl/search/srch.php".toHttpUrl().newBuilder().apply {
                addQueryParameter("lang", language)
                addUrlPart(sortOption.toUriPart(), addPath = false)
                addQueryParameterPage(page)
                addQueryParameter("q", query)
            }.build()
        }

        return parseMangasPage(client.get(url))
    }

    override val supportRelatedMangasBySearch = true

    // Manga update
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val thumbEl = document.selectFirst(mangaSelector)!!
        val imgEl = thumbEl.selectFirst("img")!!
        val infoEl = document.selectFirst("div.gallery-info.to-gall-info")

        val updatedManga = SManga.create().apply {
            url = manga.url
            title = document.selectFirst(".gallery-title h1")?.text()
                ?: imgEl.attr("alt")
            thumbnail_url = manga.thumbnail_url ?: imgEl.absUrl("data-src")
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            status = SManga.COMPLETED
            author = infoEl?.select("div.gallery-info__item:nth-child(2) a")?.joinToString { it.text() }
            genre = infoEl?.select("div.gallery-info__item:not(:nth-child(2)) a")?.joinToString { it.text() }
            description = infoEl?.select(".info-rate, .info-views")?.eachText()?.joinToString(" ")
        }

        return SMangaUpdate(
            updatedManga,
            listOf(
                SChapter.create().apply {
                    chapter_number = 0F
                    setUrlWithoutDomain(manga.url)
                    name = intl["chapter.name.default"]
                },
            ),
        )
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter) = client.get(
        getChapterUrl(chapter),
    ).asJsoup().select(mangaSelector).mapIndexed { index, element ->
        Page(index, imageUrl = element.absUrl("href"))
    }

    // Filters
    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = "$baseUrl/${if (lang == "en") "" else "$language/"}"
        val results = coroutineScope {
            listOf("tags/", "pornstars/list/", "channels/list/").map {
                async {
                    runCatching {
                        parseFilters(client.get("$url$it"))
                    }.getOrElse { emptyList() }
                }
            }.awaitAll()
        }

        val (tagsAndCats, stars, channels) = results
        val (tags, categories) = tagsAndCats.partition {
            "/tags/" in it.link
        }
        return FilterData(
            categories = categories,
            tags = tags,
            pornStars = stars,
            channels = channels,
        ).toJsonElement()
    }

    private fun parseFilters(response: Response) = response.asJsoup().select("#main-list .list-item > a")
        .map { CategoryDto(it.attr("title").ifEmpty { it.text() }, it.attr("href")) }

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOf(
            createSortSelector(intl),
            Filter.Separator(),
            Filter.Header(intl["filter.header.ignored-when-search"]),
            Filter.Separator(),
            Filter.Header(intl["filter.header.select-active-category-type"]),
            createActiveCategoryTypeSelector(intl),
            Filter.Separator(),
            Filter.Header(intl["filter.header.select-category-type-param"]),
        ) + buildList {
            val dto = data?.parseAs<FilterData>() ?: return@buildList
            with(NetworkFilters(intl, dto)) {
                listOfNotNull(
                    createCategorySelector(),
                    createTagSelector(),
                    createPornStarSelector(),
                    createChannelSelector(),
                ).let(::addAll)
            }
        },
    )

    // Preferences
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        Preferences.buildPreferences(screen.context, intl).forEach(screen::addPreference)
    }
}
