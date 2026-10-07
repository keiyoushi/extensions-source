package eu.kanade.tachiyomi.extension.ja.honto

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.clipstudioreader.ClipStudioReaderInterceptor
import keiyoushi.lib.clipstudioreader.fetchPages
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Honto :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT).withZone(ZoneId.of("Asia/Tokyo"))

    override fun OkHttpClient.Builder.configureClient() = apply {
        addCookie("safeSrchFlg" to "0")
        addInterceptor(ClipStudioReaderInterceptor())
        // pages answer 403 without a session cookie, which the top page hands out
        addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (response.code != 403 || request.url.host != baseUrl.toHttpUrl().host) return@addInterceptor response
            response.close()
            chain.proceed(request.newBuilder().url(baseUrl).head().build()).close()
            chain.proceed(request)
        }
    }

    // load desktop selectors and avoid session mismatch due to different UAs
    override fun Headers.Builder.configureHeaders() = set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36")

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter().apply { state = 1 }))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter().apply { state = 2 }))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.value.orEmpty()
        val sort = filters.firstInstance<SortFilter>().value
        val path = buildString {
            append("search_0750")
            if (genre.isNotEmpty()) append("_02$genre")
            if (sort.isNotEmpty()) append("_09$sort")
            append(".html")
        }

        val searchUrl = "$baseUrl/ebook".toHttpUrl().newBuilder().apply {
            addPathSegment(path)
            if (query.isNotBlank()) addQueryParameter("k", query)
            if (filters.firstInstanceOrNull<BrowserFilter>()?.state == true) addQueryParameter("dvc", "40")
            if (filters.firstInstanceOrNull<AdultFilter>()?.state == true) addQueryParameter("adtDisp", "1")
            addQueryParameter("unt", "1")
            addQueryParameter("pgno", page.toString())
        }.build()
        val document = client.get(searchUrl).asJsoup()
        val mangas = document.select("div.stBoxLine01 > div.stProduct02").map {
            SManga.create().apply {
                title = it.selectFirst("a.dyTitle")!!.text()
                thumbnail_url = it.selectFirst("img.dyImage")?.absUrl("data-src")
                url = thumbnail_url!!.toHttpUrl().pathSegments.last().substringBefore("_")
            }
        }
        val hasNextPage = document.selectFirst("ul.stPager li.stNext a") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val details = async {
            if (!fetchDetails) return@async manga
            val document = client.get(getMangaUrl(manga)).asJsoup()
            SManga.create().apply {
                title = document.selectFirst("h1.stTitle")!!.text()
                author = document.select("p.stAuthor a").joinToString { it.text() }
                description = document.selectFirst("p.accordion-more-info__content")?.textOrNull()
                genre = document.select("#stTopicPath a[href*=/ebook/gr/]").joinToString { it.text() }
                status = if (document.selectFirst(".product-info-icon-area__conclusion-icon") != null) SManga.COMPLETED else SManga.ONGOING
                thumbnail_url = document.selectFirst("#product-detail__main-image img")?.absUrl("src")
            }
        }

        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val volumesUrl = "$baseUrl/ebook/search_0750_09-srsdspodr.html".toHttpUrl().newBuilder()
                .addQueryParameter("srsid", manga.url)
                .addQueryParameter("adtDisp", "1")
                .build()

            val firstPage = client.get(volumesUrl).asJsoup()
            val lastPage = firstPage.select("ul.stPager li a").mapNotNull { it.text().toIntOrNull() }.maxOrNull() ?: 1
            val otherPages = (2..lastPage).map { page ->
                async {
                    val pageUrl = volumesUrl.newBuilder().addQueryParameter("pgno", page.toString()).build()
                    client.get(pageUrl).asJsoup()
                }
            }

            val owned = async {
                if (firstPage.selectFirst("a[href*=reg/logout]") == null) return@async emptySet()
                val ownedUrl = "$baseUrl/my/account/downloadlist.html".toHttpUrl().newBuilder()
                    .addQueryParameter("pmd", "search")
                    .addQueryParameter("freeWord", manga.title)
                    .addQueryParameter("dispNum", "100")
                    .build()
                client.get(ownedUrl).asJsoup().select("a[onclick^=OD.browserViewer]")
                    .mapTo(HashSet()) { ARGUMENT_REGEX.findAll(it.attr("onclick")).last().groupValues[1] }
            }

            val pages = listOf(firstPage) + otherPages.awaitAll()
            val ownedIds = owned.await()

            pages.flatMap {
                it.select("div.stBoxLine01 > div.stProduct02").mapNotNull { element ->
                    val link = element.selectFirst("a.dyTitle")!!
                    val id = link.absUrl("href").toHttpUrl().pathSegments.last().removePrefix("pd_").removeSuffix(".html")
                    val isOwned = id in ownedIds
                    val isBrowser = element.select("ul.stIcon01 li").any { device -> device.text() == "ブラウザ" }
                    val isFree = !isOwned && isBrowser && element.selectFirst("span.dyPrice")?.textOrNull() == "0"
                    if (hideLocked && !isOwned && !isFree) return@mapNotNull null

                    val releaseDate = element.selectFirst("ul.stData li:contains(販売開始日)")?.textOrNull()?.substringAfter("：")
                    SChapter.create().apply {
                        url = id
                        name = if (isOwned || isFree) link.text() else "🔒 ${link.text()}"
                        date_upload = dateFormat.tryParseDate(releaseDate)
                        memo = buildJsonObject {
                            when {
                                isOwned -> put("owned", true)
                                isFree -> put("free", true)
                            }
                        }
                    }
                }
            }
        }

        SMangaUpdate(
            details.await(),
            chapterList.await(),
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/ebook/pd-series_${manga.url}.html"

    override fun getChapterUrl(chapter: SChapter): String = runBlocking { fetchViewerUrl(chapter) }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.fetchPages(fetchViewerUrl(chapter).toHttpUrl())

    private suspend fun fetchViewerUrl(chapter: SChapter): String {
        val form = when {
            "owned" in chapter.memo -> FormBody.Builder()
                .add("className", "OrdDownloadListPc")
                .add("browserViewer", "1")
                .add("prdId", chapter.url)

            "free" in chapter.memo -> FormBody.Builder()
                .add("className", "PrdDisplayElectronicBookProductSpecificationPc")
                .add("redirectToUrl", "$baseUrl/ebook/pd_${chapter.url}.html")
                .add("browserFreeReading", "1")
                .add("itemId", chapter.url)

            else -> throw Exception("Log in via WebView and purchase this volume to read.")
        }.build()

        // volumes that need a login get html instead of json
        return client.post("$baseUrl/view_interface.php", form).parseAs<ViewerResponse> {
            if (it.startsWith("{")) it else throw Exception("Log in via WebView.")
        }.url
    }

    @Serializable
    class ViewerResponse(
        val url: String,
    )

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Note: Novels only show images, not text!"),
        SortFilter(),
        GenreFilter(),
        BrowserFilter(),
        AdultFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private val ARGUMENT_REGEX = Regex("""'([^']*)'""")
    }
}
