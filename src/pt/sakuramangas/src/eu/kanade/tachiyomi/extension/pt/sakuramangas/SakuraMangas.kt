package eu.kanade.tachiyomi.extension.pt.sakuramangas

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class SakuraMangas : HttpSource() {
    override val lang = "pt-BR"

    override val supportsLatest = true

    override val name = "Sakura Mangás"

    override val baseUrl = "https://sakuramangas.org"

    private var genresSet: Set<Genre> = emptySet()
    private var demographyOptions: List<Pair<String, String>> = listOf(
        "Todos" to "",
    )
    private var classificationOptions: List<Pair<String, String>> = listOf(
        "Todos" to "",
    )
    private var orderByOptions: List<Pair<String, String>> = listOf(
        "Lidos" to "3",
    )

    override fun headersBuilder() = super.headersBuilder()
        .set("Origin", baseUrl)
        .set("Referer", "$baseUrl/")
        .set(
            "Accept-Language",
            "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7,fr;q=0.6,zh-CN;q=0.5,zh-TW;q=0.4,zh;q=0.3",
        )
        .set("Connection", "keep-alive")
        .set("X-Requested-With", "XMLHttpRequest")

    // ================================ Popular =======================================

    override fun popularMangaRequest(page: Int): Request =
        searchMangaRequest(page, "", FilterList())

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ================================ Latest =======================================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/dist/sakura/models/home/__.home_ultimos.php", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<List<String>>()

        val mangas = result.map {
            val element = Jsoup.parseBodyFragment(it, baseUrl)
            SManga.create().apply {
                title = element.selectFirst(".h5-titulo")!!.text()
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, hasNextPage = false)
    }

    // ================================ Search =======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder()
            .add("search", query)
            .add("order", "3")
            .add("offset", ((page - 1) * DEFAULT_LIMIT).toString())
            .add("limit", DEFAULT_LIMIT.toString())

        val inclGenres = mutableListOf<String>()
        val exclGenres = mutableListOf<String>()

        var demography: String? = null
        var classification: String? = null
        var orderBy: String? = null

        filters.forEach { filter ->
            when (filter) {
                is GenreList -> filter.state.forEach {
                    when (it.state) {
                        Filter.TriState.STATE_INCLUDE -> inclGenres.add(it.id)
                        Filter.TriState.STATE_EXCLUDE -> exclGenres.add(it.id)
                        else -> {}
                    }
                }

                is DemographyFilter -> demography = filter.getValue().ifEmpty { null }
                is ClassificationFilter -> classification = filter.getValue().ifEmpty { null }
                is OrderByFilter -> orderBy = filter.getValue().ifEmpty { null }
                else -> {}
            }
        }

        inclGenres.forEach { form.add("tags[]", it) }
        exclGenres.forEach { form.add("excludeTags[]", it) }

        demography?.let { form.add("demography", it) }
        classification?.let { form.add("classification", it) }
        orderBy?.let { form.add("order", it) }

        return POST("$baseUrl/dist/sakura/models/obras/__.obras_buscar.php", headers, form.build())
    }

    fun searchMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst(".h5-titulo")!!.text()
        thumbnail_url = element.selectFirst("img.img-pesquisa")?.absUrl("src")
        description = element.selectFirst(".p-sinopse")?.text()

        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SakuraMangasResultDto>()
        val seriesList =
            result.asJsoup("$baseUrl/obras/").select(".result-item").map(::searchMangaFromElement)
        return MangasPage(seriesList, result.hasMore)
    }

    // ================================ Details =======================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    @SuppressLint("SetJavaScriptEnabled")
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val interfaceName = randomString()

        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val jsInterface = JsInterface(latch)
        var webView: WebView? = null
        var finalUrl: String? = null

        Log.d("SakuraMangas", "fetching manga details for ${manga.url}")

        handler.post {
            val innerWv = WebView(Injekt.get<Application>())

            webView = innerWv
            innerWv.settings.domStorageEnabled = true
            innerWv.settings.javaScriptEnabled = true
            innerWv.settings.blockNetworkImage = true
            innerWv.settings.userAgentString = headers["User-Agent"]
            innerWv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            innerWv.addJavascriptInterface(jsInterface, interfaceName)

            innerWv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    finalUrl = url
                    Log.d("SakuraMangas", "Page loaded for $url, injecting script")
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(
                        """
                            const interval = setInterval(() => {
                                const isLoaded = !!document.querySelector('.h1-titulo').textContent
                                if (!isLoaded) {
                                    return;
                                }
                                clearInterval(interval);
                                window.$interfaceName.passHtmlPayload(document.documentElement.outerHTML);
                            }, 100);
                        """.trimIndent(),
                    ) {}
                }
            }

            innerWv.loadUrl(
                "$baseUrl${manga.url}",
                headers.toMap(),
            )
        }

        latch.await(30, TimeUnit.SECONDS)
        handler.post { webView?.destroy() }

        Log.d("SakuraMangas", "manga details fetched for ${manga.url}")

        if (latch.count == 1L) {
            throw Exception("Timed out decrypting manga info")
        }

        Log.d("SakuraMangas", "parsing manga details for ${manga.url}")

        val document = Jsoup.parse(jsInterface.html, finalUrl!!)

        val mangaResult = SManga.create().apply {
            finalUrl?.let { manga.setUrlWithoutDomain(it) }
            document.selectFirst(".img-capa img")?.let { thumbnail_url = it.attr("abs:src") }
            document.selectFirst(".autor")?.text().let { author = it }
            document.selectFirst(".sinopse-modal")?.text().let { description = it }
            document.selectFirst("#status")?.text().let {
                status = when (it) {
                    "Concluído" -> SManga.COMPLETED
                    "Em andamento" -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
            }
            document.select(".generos > *").joinToString { it.text() }.takeIf { it.isNotEmpty() }
                ?.let { genre = it }
        }

        Log.d("SakuraMangas", "manga details parsed for ${manga.url}")

        return Observable.just(mangaResult)
    }

    override fun mangaDetailsParse(response: Response): SManga = throw Exception("Not used")

    // ================================ Chapters =======================================

    @SuppressLint("SetJavaScriptEnabled")
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val interfaceName = randomString()

        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val jsInterface = JsInterface(latch)
        var webView: WebView? = null
        var finalUrl: String? = null

        Log.d("SakuraMangas", "fetching chapter list for ${manga.url}")

        handler.post {
            val innerWv = WebView(Injekt.get<Application>())

            webView = innerWv
            innerWv.settings.domStorageEnabled = true
            innerWv.settings.javaScriptEnabled = true
            innerWv.settings.blockNetworkImage = true
            innerWv.settings.userAgentString = headers["User-Agent"]
            innerWv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            innerWv.addJavascriptInterface(jsInterface, interfaceName)

            innerWv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    finalUrl = url
                    Log.d("SakuraMangas", "Page loaded for $url, injecting script")
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(
                        """
                            const interval = setInterval(() => {
                                const btnMore = document.querySelector('#ver-mais')

                                if (window.getComputedStyle(btnMore).display === 'none') {
                                    clearInterval(interval);
                                    window.$interfaceName.passHtmlPayload(document.documentElement.outerHTML);
                                    return;
                                }

                                if (!btnMore.disabled) {
                                    btnMore.click();
                                }
                            }, 1000);
                        """.trimIndent(),
                    ) {}
                }
            }

            innerWv.loadUrl(
                "$baseUrl${manga.url}",
                headers.toMap(),
            )
        }

        latch.await(60, TimeUnit.SECONDS)
        handler.post { webView?.destroy() }

        Log.d("SakuraMangas", "chapter list fetched for ${manga.url}")

        if (latch.count == 1L) {
            throw Exception("Timed out decrypting chapter list")
        }

        Log.d("SakuraMangas", "parsing chapter list for ${manga.url}")

        val document = Jsoup.parse(jsInterface.html, finalUrl!!)

        val chapters = document.select(".capitulo-item").map(::chapterFromElement)

        Log.d("SakuraMangas", "chapter list parsed for ${manga.url}")

        return Observable.just(chapters)
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw Exception("Not used")

    fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = buildString {
            element.selectFirst(".num-capitulo")
                ?.text()
                ?.let { append(it) }

            element.selectFirst(".cap-titulo")
                ?.text()
                ?.takeIf { it.isNotBlank() }
                ?.let { append(" - $it") }
        }
        scanlator = element.selectFirst(".scan-nome")?.text()
        chapter_number =
            element
                .selectFirst(".num-capitulo")
                ?.attr("data-chapter")
                ?.toFloatOrNull() ?: 0F
        date_upload = element.selectFirst(".cap-data")?.text()?.toDate() ?: 0L
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    // ================================ Pages =======================================

    @SuppressLint("SetJavaScriptEnabled")
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val interfaceName = randomString()

        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val jsInterface = JsInterface(latch)
        var webView: WebView? = null

        handler.post {
            val innerWv = WebView(Injekt.get<Application>())

            webView = innerWv
            innerWv.settings.domStorageEnabled = true
            innerWv.settings.javaScriptEnabled = true
            innerWv.settings.blockNetworkImage = true
            innerWv.settings.userAgentString = headers["User-Agent"]
            innerWv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            innerWv.addJavascriptInterface(jsInterface, interfaceName)

            innerWv.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    view?.evaluateJavascript(
                        """
                            Object.defineProperty(Object.prototype, 'imageUrls', {
                                set: function(value) {
                                    window.$interfaceName.passImagesPayload(JSON.stringify(value));
                                    Object.defineProperty(this, '_imageUrls', {
                                        value: value,
                                        writable: true,
                                        enumerable: false,
                                        configurable: true
                                    });
                                },
                                get: function() {
                                    return this._imageUrls;
                                },
                                enumerable: false,
                                configurable: true
                            });
                        """.trimIndent(),
                    ) {}
                }
            }

            innerWv.loadUrl(
                "$baseUrl${chapter.url}",
                headers.toMap(),
            )
        }

        latch.await(30, TimeUnit.SECONDS)
        handler.post { webView?.destroy() }

        if (latch.count == 1L) {
            throw Exception("Timed out decrypting image links")
        }

        val images = jsInterface
            .images
            .mapIndexed { i, url ->
                Page(i, imageUrl = "${baseUrl}${chapter.url}/$url".toHttpUrl().toString())
            }

        return Observable.just(images)
    }

    override fun pageListParse(response: Response): List<Page> = throw Exception("Not used")

    override fun imageUrlParse(response: Response): String = ""

    override fun getFilterList(): FilterList {
        thread {
            fetchFilters()
        }

        return FilterList(
            OrderByFilter("Ordenar por", orderByOptions, "order"),
            DemographyFilter("Demografia", demographyOptions, "demography"),
            ClassificationFilter("Classificação", classificationOptions, "classification"),
            GenreList(
                title = "Gêneros",
                genres = genresSet.toTypedArray(),
            ),
        )
    }

    private fun fetchFilters() {
        if (genresSet.isNotEmpty()) {
            return
        }

        try {
            val document = client
                .newCall(GET("$baseUrl/obras/", headers))
                .execute()
                .asJsoup()

            genresSet = document.select(".genero-badge").map { element ->
                val id = element.attr("data-value")
                Genre(element.ownText(), id)
            }.toSet()

            val demoOpts = document.select("select#demografia-select option").mapNotNull { opt ->
                val value = opt.attr("value").orEmpty()
                val text = opt.text().trim()
                if (text.isEmpty()) null else text to value
            }
            if (demoOpts.isNotEmpty()) demographyOptions = demoOpts

            val classOpts =
                document.select("select#classificacao-select option").mapNotNull { opt ->
                    val value = opt.attr("value").orEmpty()
                    val text = opt.text().trim()
                    if (text.isEmpty()) null else text to value
                }
            if (classOpts.isNotEmpty()) classificationOptions = classOpts

            val orderOptions = document.select("select#ordenar-por option").mapNotNull { opt ->
                val value = opt.attr("value").orEmpty()
                val text = opt.text().trim()
                if (text.isEmpty()) null else text to value
            }
            if (orderOptions.isNotEmpty()) orderByOptions = orderOptions
        } catch (e: Exception) {
            Log.e("SakuraMangas", "failed to fetch genres", e)
        }
    }

    private fun String.toDate(): Long {
        val trimmedDate = this.split(" ")

        if (trimmedDate[0] != "Há") return 0L

        val number = trimmedDate[1].toIntOrNull() ?: return 0L

        val unit = trimmedDate[2]

        val javaUnit = when (unit) {
            "ano", "anos" -> Calendar.YEAR
            "mês", "meses" -> Calendar.MONTH
            "semana", "semanas" -> Calendar.WEEK_OF_MONTH
            "dia", "dias" -> Calendar.DAY_OF_MONTH
            "hora", "horas" -> Calendar.HOUR
            "minuto", "minutos" -> Calendar.MINUTE
            "segundo", "segundos" -> Calendar.SECOND
            else -> return 0L
        }

        val now = Calendar.getInstance()

        now.add(javaUnit, -number)

        return now.timeInMillis
    }

    private fun randomString(length: Int = 10): String {
        val charPool = ('a'..'z') + ('A'..'Z')
        return List(length) { charPool.random() }.joinToString("")
    }

    internal class JsInterface(private val latch: CountDownLatch) {
        var images: List<String> = listOf()
            private set

        var html: String = ""
            private set

        @JavascriptInterface
        @Suppress("UNUSED")
        fun passImagesPayload(rawData: String) {
            try {
                images = rawData.parseAs<List<String>>()
                latch.countDown()
            } catch (_: Exception) {
                return
            }
        }

        @JavascriptInterface
        @Suppress("UNUSED")
        fun passHtmlPayload(rawData: String) {
            try {
                html = rawData
                latch.countDown()
            } catch (_: Exception) {
                return
            }
        }
    }

    companion object {
        private const val DEFAULT_LIMIT = 15
    }
}
