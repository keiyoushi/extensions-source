package eu.kanade.tachiyomi.extension.all.mangafire

import android.annotation.SuppressLint
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
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
import keiyoushi.utils.tryParse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class MangaFire(
    override val lang: String,
    private val langCode: String = lang,
) : HttpSource(),
    ConfigurableSource {
    override val name = "MangaFire"

    override val baseUrl = "https://mangafire.to"

    override val supportsLatest = true
    private val preferences by getPreferencesLazy()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor)
        .rateLimit(2)
        .apply {
            val naiveTrustManager =
                @SuppressLint("CustomX509TrustManager")
                object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                    override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
                    override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
                }

            val insecureSocketFactory = SSLContext.getInstance("SSL").apply {
                val trustAllCerts = arrayOf<TrustManager>(naiveTrustManager)
                init(null, trustAllCerts, SecureRandom())
            }.socketFactory

            sslSocketFactory(insecureSocketFactory, naiveTrustManager)
            hostnameVerifier { _, _ -> true }
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val webViewHelper = WebViewHelper(client, headers)

    // dirty hack to disable suggested mangas on Komikku
    // we don't want to spawn N webviews for N search token
    // https://github.com/komikku-app/komikku/blob/4323fd5841b390213aa4c4af77e07ad42eb423fc/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/CatalogueSource.kt#L176-L184
    @Suppress("Unused")
    @JvmName("getDisableRelatedMangasBySearch")
    fun disableRelatedMangasBySearch() = true

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(
        page,
        "",
        FilterList(SortFilter(defaultValue = "most_viewed")),
    )

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(
        page,
        "",
        FilterList(SortFilter(defaultValue = "recently_updated")),
    )

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // =============================== Search ===============================

    private val vrfCache = object : LinkedHashMap<String, String>() {
        override fun removeEldestEntry(eldest: Map.Entry<String?, String?>?) = size > 20
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val stdQuery = query.replace("\"", " ").trim()

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("filter")

            if (stdQuery.isNotBlank()) {
                addQueryParameter("keyword", stdQuery)
            }

            val filterList = filters.ifEmpty { getFilterList() }
            filterList.filterIsInstance<UriFilter>().forEach {
                it.addToUri(this)
            }

            addQueryParameter("language[]", langCode)
            addQueryParameter("page", page.toString())

            if (stdQuery.isNotBlank()) {
                val vrf = vrfCache.get(stdQuery)
                    ?: runBlocking {
                        webViewHelper.loadInWebView(
                            url = "$baseUrl/home",
                            requestIntercept = { request ->
                                val url = request.url
                                if (
                                    url.host == "mangafire.to" &&
                                    url.encodedPath.orEmpty().contains("ajax/manga/search")
                                ) {
                                    WebViewHelper.RequestIntercept.Capture
                                } else {
                                    WebViewHelper.RequestIntercept.Block
                                }
                            },
                            onPageFinish = { view ->
                                view.evaluateJavascript(
                                    """
                                    $(function() {
                                      setInterval(() => {
                                        $(".search-inner input[name=keyword]").val("$stdQuery").trigger("keyup");
                                      }, 1000);
                                    });
                                    """.trimIndent(),
                                ) {}
                            },
                        )
                    }.toHttpUrl().queryParameter("vrf")
                        ?.takeIf { it.isNotBlank() }
                        ?.also { vrfCache.put(stdQuery, it) }
                    ?: throw Exception("Unable to find vrf token")

                addQueryParameter("vrf", vrf)
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        var entries = document.select(searchMangaSelector()).map(::searchMangaFromElement)
        if (preferences.getBoolean(SHOW_VOLUME_PREF, false)) {
            entries = entries.flatMapTo(ArrayList(entries.size * 2)) { manga ->
                val volume = SManga.create().apply {
                    url = manga.url + VOLUME_URL_SUFFIX
                    title = VOLUME_TITLE_PREFIX + manga.title
                    thumbnail_url = manga.thumbnail_url
                }
                listOf(manga, volume)
            }
        }
        val hasNextPage = document.selectFirst(searchMangaNextPageSelector()) != null
        return MangasPage(entries, hasNextPage)
    }

    private fun searchMangaNextPageSelector() = ".page-item.active + .page-item .page-link"

    private fun searchMangaSelector() = ".original.card-lg .unit .inner"

    private fun searchMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst(".info > a")!!.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.ownText()
        }
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    // =============================== Filters ==============================

    override fun getFilterList() = FilterList(
        TypeFilter(),
        GenreFilter(),
        GenreModeFilter(),
        StatusFilter(),
        YearFilter(),
        MinChapterFilter(),
        SortFilter(),
    )

    // =========================== Manga Details ============================

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url.removeSuffix(VOLUME_URL_SUFFIX)

    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup()).apply {
        if (response.request.url.fragment == VOLUME_URL_FRAGMENT) {
            title = VOLUME_TITLE_PREFIX + title
        }
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        with(document.selectFirst(".main-inner:not(.manga-bottom)")!!) {
            title = selectFirst("h1")!!.text()
            thumbnail_url = selectFirst(".poster img")?.attr("src")
            status = selectFirst(".info > p").parseStatus()
            description = buildString {
                document.selectFirst("#synopsis .modal-content")?.textNodes()?.let {
                    append(it.joinToString("\n\n"))
                }

                selectFirst("h6")?.let {
                    append("\n\nAlternative title: ${it.text()}")
                }
            }.trim()

            selectFirst(".meta")?.let {
                author = it.selectFirst("span:contains(Author:) + span")?.text()
                val type = it.selectFirst("span:contains(Type:) + span")?.text()
                val genres = it.selectFirst("span:contains(Genres:) + span")?.text()
                genre = listOfNotNull(type, genres).joinToString()
            }
        }
    }

    // MangaFire marks manga as "completed" when their original publication is completed,
    // even if their translation is not complete, so we use the "PUBLISHING_FINISHED" status.
    private fun Element?.parseStatus(): Int = when (this?.text()?.lowercase()) {
        "releasing" -> SManga.ONGOING
        "completed" -> SManga.PUBLISHING_FINISHED
        "on_hiatus" -> SManga.ON_HIATUS
        "discontinued" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.substringBeforeLast("#")

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.removeSuffix(VOLUME_URL_SUFFIX).substringAfterLast(".")
        val type = if (manga.url.endsWith(VOLUME_URL_SUFFIX)) "volume" else "chapter"

        return GET("$baseUrl/ajax/manga/$mangaId/$type/$langCode", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val isVolume = response.request.url.pathSegments.contains("volume")

        val mangaList = response.parseAs<ResponseDto<String>>().result
            .toBodyFragment()
            .select(if (isVolume) ".vol-list > .item" else "li")

        val abbrPrefix = if (isVolume) "Vol" else "Chap"
        val fullPrefix = if (isVolume) "Volume" else "Chapter"

        return mangaList.map { m ->
            val link = m.selectFirst("a")!!

            val number = m.attr("data-number")
            val dateStr = m.select("span").getOrNull(1)?.text() ?: ""

            SChapter.create().apply {
                setUrlWithoutDomain(link.attr("href"))
                chapter_number = number.toFloatOrNull() ?: -1f
                name = run {
                    val name = m.selectFirst("span")!!.text()
                    val prefix = "$abbrPrefix $number: "
                    if (!name.startsWith(prefix)) return@run name
                    val realName = name.removePrefix(prefix)
                    if (realName.contains(number)) realName else "$fullPrefix $number: $realName"
                }
                date_upload = dateFormat.tryParse(dateStr)
            }
        }
    }

    // =============================== Pages ================================

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val intercepted = runBlocking {
            webViewHelper.loadInWebView(
                url = "$baseUrl${chapter.url}",
                requestIntercept = { request ->
                    val url = request.url
                    if (
                        url.host == "mangafire.to" &&
                        url.encodedPath.orEmpty().contains("ajax/read")
                    ) {
                        if (setOf("ajax/read/chapter", "ajax/read/volume").any { url.encodedPath!!.contains(it) }) {
                            WebViewHelper.RequestIntercept.Capture
                        } else {
                            // need to allow other call to ajax/read
                            WebViewHelper.RequestIntercept.Allow
                        }
                    } else {
                        WebViewHelper.RequestIntercept.Block
                    }
                },
                onPageFinish = {},
            )
        }
        if (intercepted.toHttpUrl().queryParameter("vrf") == null) {
            throw Exception("Unable to find vrf token")
        }

        return client.newCall(GET(intercepted, headers))
            .asObservableSuccess().map {
                it.parseAs<ResponseDto<PageListDto>>().result
                    .pages.mapIndexed { index, image ->
                        val url = image.url
                        val offset = image.offset
                        val imageUrl =
                            if (offset > 0) "$url#${ImageInterceptor.SCRAMBLED}_$offset" else url

                        Page(index, imageUrl = imageUrl)
                    }
            }
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    @Serializable
    class PageListDto(private val images: List<List<JsonPrimitive>>) {
        val pages
            get() = images.map {
                Image(it[0].content, it[2].int)
            }
    }

    class Image(val url: String, val offset: Int)

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================ Preferences =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_VOLUME_PREF
            title = "Show volume entries in search result"
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    // ============================= Utilities ==============================

    @Serializable
    class ResponseDto<T>(
        val result: T,
    )

    private fun String.toBodyFragment(): Document = Jsoup.parseBodyFragment(this, baseUrl)

    companion object {
        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        private const val SHOW_VOLUME_PREF = "show_volume"

        private const val VOLUME_URL_FRAGMENT = "vol"
        private const val VOLUME_URL_SUFFIX = "#$VOLUME_URL_FRAGMENT"
        private const val VOLUME_TITLE_PREFIX = "[VOL] "
    }
}
