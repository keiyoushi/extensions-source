package eu.kanade.tachiyomi.extension.all.danbooru

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Danbooru :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest: Boolean = true

    // Make image requests mimic a standard browser <img> fetch to bypass CF 403s on the CDN
    private val cdnInterceptor = Interceptor { chain ->
        val request = chain.request()
        if (request.url.host == "cdn.donmai.us") {
            val newRequest = request.newBuilder()
                .removeHeader("Cookie") // CF flags CDN requests containing main-domain session cookies
                .header("Accept", "image/avif,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5")
                .header("Sec-Fetch-Dest", "image")
                .header("Sec-Fetch-Mode", "no-cors")
                .header("Sec-Fetch-Site", "same-site")
                .build()
            return@Interceptor chain.proceed(newRequest)
        }
        chain.proceed(request)
    }

    override val client = network.client.newBuilder()
        .addInterceptor(cdnInterceptor)
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)

    private val preference by getPreferencesLazy()

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList())

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(filterOrder("created_at")))

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/pools/gallery".toHttpUrl().newBuilder()

        url.setEncodedQueryParameter("search[category]", "series")

        filters.forEach {
            when (it) {
                is FilterTags -> if (it.state.isNotBlank()) {
                    url.addQueryParameter("search[post_tags_match]", it.state)
                }
                is FilterDescription -> if (it.state.isNotBlank()) {
                    url.addQueryParameter("search[description_matches]", it.state)
                }
                is FilterIsDeleted -> if (it.state) {
                    url.addEncodedQueryParameter("search[is_deleted]", "true")
                }
                is FilterCategory -> {
                    url.setEncodedQueryParameter("search[category]", it.selected)
                }
                is FilterOrder -> if (it.selected != null) {
                    url.addEncodedQueryParameter("search[order]", it.selected)
                }
                else -> {}
            }
        }

        url.addEncodedQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("search[name_contains]", query)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select("article.post-preview").map {
            searchMangaFromElement(it)
        }
        val hasNextPage = document.selectFirst("a.paginator-next") != null

        return MangasPage(entries, hasNextPage)
    }

    private fun searchMangaFromElement(element: Element) = SManga.create().apply {
        url = element.selectFirst(".post-preview-link")!!.attr("href")
        title = element.selectFirst("div.text-center")!!.text()

        thumbnail_url = element.selectFirst("source")?.attr("srcset")
            ?.substringAfterLast(',')?.trim()
            ?.substringBeforeLast(' ')?.trimStart()
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("http://") || query.startsWith("https://")) {
            val url = query.toHttpUrlOrNull()
            if (url != null && url.host == baseUrl.toHttpUrl().host) {
                val path = url.pathSegments
                if (path.size >= 2 && path[0] == "pools") {
                    val id = path[1]
                    val manga = SManga.create().apply {
                        this.url = "/pools/$id"
                    }
                    return fetchMangaDetails(manga).map { MangasPage(listOf(it), false) }
                }
                throw Exception("Unsupported URL")
            }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()

        setUrlWithoutDomain(document.location())
        title = document.selectFirst(".pool-category-series, .pool-category-collection")?.text()
            ?: document.selectFirst("h1")!!.text()
        description = document.getElementById("description")?.wholeText()
        author = document.selectFirst("#description a[href*=artists]")?.ownText()
        artist = author
        update_strategy = if (!preference.splitChaptersPref) {
            UpdateStrategy.ONLY_FETCH_ONCE
        } else {
            UpdateStrategy.ALWAYS_UPDATE
        }
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl${manga.url}.json", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<Pool>()

        return if (preference.splitChaptersPref) {
            data.postIds.mapIndexed { index, id ->
                SChapter.create().apply {
                    url = "/posts/$id"
                    name = "Post ${index + 1}"
                    chapter_number = index + 1f
                }
            }.reversed().apply {
                if (isNotEmpty()) {
                    this[0].date_upload = dateFormat.tryParse(data.updatedAt)
                }
            }
        } else {
            listOf(
                SChapter.create().apply {
                    url = "/pools/${data.id}"
                    name = "Oneshot"
                    date_upload = dateFormat.tryParse(data.updatedAt)
                    chapter_number = 0F
                },
            )
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}.json", headers)

    override fun pageListParse(response: Response): List<Page> = if (response.request.url.toString().contains("/posts/")) {
        val data = response.parseAs<Post>()
        val imageUrl = data.bestUrl.let { if (it.startsWith("http")) it else "$baseUrl$it" }
        listOf(
            Page(index = 0, imageUrl = imageUrl),
        )
    } else {
        val data = response.parseAs<Pool>()

        data.postIds.mapIndexed { index, id ->
            Page(index, url = "/posts/$id")
        }
    }

    override fun imageUrlRequest(page: Page): Request = GET("$baseUrl${page.url}.json", headers)

    override fun imageUrlParse(response: Response): String {
        val url = response.parseAs<Post>().bestUrl
        return if (url.startsWith("http")) url else "$baseUrl$url"
    }

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        FilterDescription(),
        FilterTags(),
        FilterIsDeleted(),
        FilterCategory(),
        FilterOrder(),
    )

    // ============================= Utilities =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = CHAPTER_LIST_PREF
            title = "Split posts into individual chapters"
            summary = """
                Instead of showing one 'OneShot' chapter,
                each post will be it's own chapter
            """.trimIndent()
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private val SharedPreferences.splitChaptersPref: Boolean
        get() = getBoolean(CHAPTER_LIST_PREF, false)
}

private const val CHAPTER_LIST_PREF = "prefChapterList"
