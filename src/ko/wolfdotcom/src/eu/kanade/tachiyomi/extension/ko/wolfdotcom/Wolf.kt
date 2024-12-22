package eu.kanade.tachiyomi.extension.ko.wolfdotcom

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.net.URLEncoder
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

open class Wolf(
    name: String,
    private val browsePath: String,
    private val entryPath: String,
    private val readerPath: String,
) : HttpSource(), ConfigurableSource {

    override val name = "늑대닷컴 - $name"

    override val lang = "ko"

    override val baseUrl: String
        get() = "https://wfwf$domainNumber.com"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::domainNumberInterceptor)
        .build()

    private val json: Json by injectLazy()

    private val preference: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return fetchSearchManga(page, "", POPULAR)
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return fetchSearchManga(page, "", LATEST)
    }

    private var searchFilters: List<FilterData> = emptyList()
    private var filterParseError = false

    override fun getFilterList(): FilterList {
        val filters: MutableList<Filter<*>> = mutableListOf(
            SortFilter(),
        )

        if (searchFilters.isNotEmpty()) {
            filters.add(
                SearchFilter(searchFilters),
            )
        } else if (filterParseError) {
            filters.add(
                Filter.Header("unable to parse filters"),
            )
        } else {
            filters.add(
                Filter.Header("press 'reset' to attempt to load more filters"),
            )
        }

        return FilterList(filters)
    }

    protected open fun parseSearchFilters(document: Document) {
        if (searchFilters.isNotEmpty() || filterParseError) return

        try {
            val displayName = document.select(".sub-tab > a").eachText()
            assert(displayName.size == 3)
            searchFilters =
                document.select(".tab-day > a, .tab-genre1 > a, .tab-genre2 > a, .tab-alphabet > a")
                    .map {
                        val url = it.absUrl("href").toHttpUrl()
                        val type = url.queryParameter("type1")!!
                        FilterData(
                            type = type,
                            typeDisplayName = when (type) {
                                "day", "complete" -> displayName[0]
                                "genre" -> displayName[1]
                                "alphabet" -> displayName[2]
                                else -> null
                            },
                            value = url.queryParameter("type2"),
                            valueDisplayName = it.ownText(),
                        )
                    }
        } catch (e: Throwable) {
            Log.e(name, "error parsing filters", e)
            filterParseError = true
        }
    }

    private lateinit var browseCache: List<List<BrowseItem>>

    class BrowseItem(
        val id: Int,
        val title: String,
        val cover: String?,
    )

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.isNotBlank()) {
            return querySearch(query)
        }

        return if (page == 1) {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map {
                    parseBrowsePage(it)
                    paginatedBrowsePage(0)
                }
        } else {
            Observable.just(
                paginatedBrowsePage(page - 1),
            )
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/$browsePath".toHttpUrl().newBuilder().apply {
            filters.filterIsInstance<UrlPartFilter>().forEach { filter ->
                filter.addToUrl(this)
            }
        }.build()

        return GET(url, headers)
    }

    private fun parseBrowsePage(response: Response) {
        val document = response.asJsoup()

        parseSearchFilters(document)

        browseCache = document.select(".webtoon-list > ul > li > a").map {
            val id = it.absUrl("href").toHttpUrl()
                .queryParameter("toon")!!.toInt()

            BrowseItem(
                id = id,
                title = it.selectFirst(".txt > .subject")!!.ownText(),
                cover = it.selectFirst(".img > img")?.attr("data-original"),
            )
        }.chunked(20)
    }

    private fun paginatedBrowsePage(index: Int): MangasPage {
        return MangasPage(
            browseCache[index].map {
                SManga.create().apply {
                    url = it.id.toString()
                    title = it.title
                    thumbnail_url = it.cover
                }
            },
            browseCache.lastIndex > index,
        )
    }

    private val specialChars = Regex("""[^\p{InHangul_Syllables}0-9a-z ]""", RegexOption.IGNORE_CASE)
    private val styleImage = Regex("""background-image:url\(([^)]+)\)""")

    private fun querySearch(query: String): Observable<MangasPage> {
        if (query.length < 2) {
            throw Exception("두 글자 이상 입력 해주세요.")
        }
        val stdQuery = query.replace(specialChars, "")
        val searchUrl = "$baseUrl/search.html?q=${URLEncoder.encode(stdQuery, "EUC-KR")}"

        return client.newCall(GET(searchUrl, headers))
            .asObservableSuccess()
            .map { response ->
                val document = Jsoup.parseBodyFragment(response.body.string(), searchUrl)
                val entries = document.select("article.searchItem")
                    .filter { el ->
                        el.selectFirst("a.searchLink")!!.attr("href").contains(entryPath)
                    }
                    .map { el ->
                        val mangaUrl = el.selectFirst("a.searchLink")!!.absUrl("href")
                            .toHttpUrl()
                        SManga.create().apply {
                            url = mangaUrl.queryParameter("toon")!!
                            title = el.selectFirst(".searchDetailTitle")!!.text()
                            thumbnail_url = el.selectFirst(".searchPng")
                                ?.attr("style")
                                ?.let {
                                    styleImage.find(it)?.groupValues?.get(1)
                                }
                        }
                    }

                MangasPage(entries, false)
            }
    }

    override fun getMangaUrl(manga: SManga): String {
        return baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(entryPath)
            .addQueryParameter("toon", manga.url)
            .toString()
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(getMangaUrl(manga), headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(".text-box h1")!!.text()
            thumbnail_url = document.selectFirst(".img-box img")?.absUrl("src")
            description = document.selectFirst(".text-box .txt")?.text()
            genre = document.selectFirst(".text-box .sub:has(> strong:contains(장르))")?.ownText()?.replace("/", ", ")
            author = document.selectFirst(".text-box .sub:has(> strong:contains(작가))")?.ownText()?.replace("/", ", ")
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    @Serializable
    class ChapterUrl(
        val toon: String,
        val num: String,
    )

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(".webtoon-bbs-list a.view_open").map { el ->
            val chapUrl = el.absUrl("href").toHttpUrl()
            SChapter.create().apply {
                url = json.encodeToString(
                    ChapterUrl(
                        chapUrl.queryParameter("toon")!!,
                        chapUrl.queryParameter("num")!!,
                    ),
                )
                name = el.selectFirst(".subject")!!.ownText()
                date_upload = el.selectFirst(".date")?.text().parseDate()
            }
        }
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

    private fun String?.parseDate(): Long {
        this ?: return 0L

        return try {
            dateFormat.parse(this)!!.time
        } catch (_: ParseException) {
            0L
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val chapUrl = json.decodeFromString<ChapterUrl>(chapter.url)

        return baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(readerPath)
            .addQueryParameter("toon", chapUrl.toon)
            .addQueryParameter("num", chapUrl.num)
            .toString()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(getChapterUrl(chapter), headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select(".image-view img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.absUrl("data-original"))
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_DOMAIN_NUM
            title = "도메인 번호"
            setOnPreferenceChangeListener { _, newValue ->
                val value = newValue as String
                if (value.isEmpty() || value.toIntOrNull() == null) {
                    false
                } else {
                    domainNumber = value.trim()
                    false
                }
            }
        }.also(screen::addPreference)
    }

    private var domainNumber = ""
        get() {
            val currentValue = field
            if (currentValue.isNotEmpty()) return currentValue

            val prefValue = preference.getString(PREF_DOMAIN_NUM, "")!!
            val prefDefaultValue = preference.getString(PREF_DOMAIN_NUM_DEFAULT, "")!!

            if (prefDefaultValue != DEFAULT_DOMAIN_NUMBER) {
                preference.edit()
                    .putString(PREF_DOMAIN_NUM_DEFAULT, DEFAULT_DOMAIN_NUMBER)
                    .putString(PREF_DOMAIN_NUM, DEFAULT_DOMAIN_NUMBER)
                    .apply()

                field = DEFAULT_DOMAIN_NUMBER
                return DEFAULT_DOMAIN_NUMBER
            }

            if (prefValue.isNotEmpty()) {
                field = prefValue
                return prefValue
            }

            return DEFAULT_DOMAIN_NUMBER
        }
        set(value) {
            preference.edit().putString(PREF_DOMAIN_NUM, value).apply()

            field = value
        }

    private fun domainNumberInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val url = request.url.toString()

        if (url.contains(domainRegex)) {
            val document = Jsoup.parse(response.peekBody(Long.MAX_VALUE).string())
            val newUrl = document.selectFirst("""#pop-content a[href~=^https?://wfwf\d+\.com]""")
                ?: return response

            response.close()

            val newDomainNum = domainRegex.find(newUrl.attr("href"))?.groupValues?.get(1)
                ?: throw IOException("Failed to update domain number")

            domainNumber = newDomainNum.trim()

            return chain.proceed(
                request.newBuilder()
                    .url(
                        request.url.newBuilder()
                            .host(baseUrl.toHttpUrl().host)
                            .build(),
                    )
                    .build(),
            )
        }

        return response
    }

    private val domainRegex = Regex("""^https?://wfwf(\d+)\.com""")

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }
    override fun popularMangaParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }
    override fun popularMangaRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }
    override fun latestUpdatesParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }
    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }
    override fun searchMangaParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }
}

private const val PREF_DOMAIN_NUM = "domain_number"
private const val PREF_DOMAIN_NUM_DEFAULT = "domain_number_default"
