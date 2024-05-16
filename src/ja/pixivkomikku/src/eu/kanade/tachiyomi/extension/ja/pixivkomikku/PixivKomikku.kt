package eu.kanade.tachiyomi.extension.ja.pixivkomikku

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.lib.dataimage.DataImageInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CountDownLatch

class PixivKomikku : HttpSource() {
    override val lang: String = "ja"
    override val supportsLatest = true
    override val name = "Pixiv Komikku"
    override val baseUrl = "https://comic.pixiv.net"

    private val json: Json by injectLazy()
    private val alreadyLoadedPopularMangaIds = mutableSetOf<Int>()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(DataImageInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("X-Requested-With", "pixivcomic")

    override fun popularMangaRequest(page: Int): Request {
        if (page == 1) alreadyLoadedPopularMangaIds.clear()

        return GET(
            "$baseUrl/api/app/rankings/popularity?label=総合&count=${
            POPULAR_MANGA_COUNT_PER_PAGE * page}",
            headers,
        )
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val popular = json.decodeFromString<Popular>(response.body.string())

        val mangas = popular.data.ranking.filterNot {
            alreadyLoadedPopularMangaIds.contains(it.id)
        }.map { manga ->
            SManga.create().apply {
                title = manga.title
                thumbnail_url = manga.main_image_url
                setUrlWithoutDomain("/api/app/works/v5/${manga.id}")

                alreadyLoadedPopularMangaIds.add(manga.id)
            }
        }
        return MangasPage(mangas, true)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/api/app/works/recent_updates?page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val latest = json.decodeFromString<Latest>(response.body.string())

        val mangas = latest.data.official_works.map { manga ->
            SManga.create().apply {
                title = manga.name
                thumbnail_url = manga.image.main
                setUrlWithoutDomain("/api/app/works/v5/${manga.id}")
            }
        }
        return MangasPage(mangas, true)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.startsWith("#")) {
            val tag = query.removePrefix("#")
            return GET("$baseUrl/api/app/tags/$tag/works/v2?page=$page", headers)
        }

        filters.forEach { filter ->
            when (filter) {
                is Category ->
                    if (filter.state != 0) {
                        return GET(
                            "$baseUrl/api/app/categories/${
                            filter.values[filter.state]}/works?page=$page",
                            headers,
                        )
                    }
                is Tag ->
                    if (filter.state.isNotBlank()) {
                        return GET(
                            "$baseUrl/api/app/tags/${
                            filter.state}/works/v2?page=$page",
                            headers,
                        )
                    }
                else -> {}
            }
        }

        return GET("$baseUrl/api/app/works/search/v2/$query?page=$page", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val search = json.decodeFromString<Search>(response.body.string())

        val mangas = search.data.official_works.map { manga ->
            SManga.create().apply {
                title = manga.name
                thumbnail_url = manga.image.main
                setUrlWithoutDomain("/api/app/works/v5/${manga.id}")
            }
        }

        return if (mangas.size >= 21) {
            MangasPage(mangas, true)
        } else {
            MangasPage(mangas, false)
        }
    }

    override fun getFilterList() = FilterList(CategoryHeader(), Category(), TagHeader(), Tag())

    private class CategoryHeader : Filter.Header(CATEGORY_HEADER_TEXT)

    private class Category : Filter.Select<String>("Category", categories)

    private class TagHeader : Filter.Header(TAG_HEADER_TEXT)

    private class Tag : Filter.Text("Tag")

    override fun getMangaUrl(manga: SManga): String {
        val mangaId = manga.url.substringAfterLast("/")

        return "$baseUrl/works/$mangaId"
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = json.decodeFromString<Manga>(response.body.string())
        val mangaInfo = manga.data.official_work

        return SManga.create().apply {
            description = Jsoup.parse(mangaInfo.description).wholeText()
            author = mangaInfo.author

            val categories = mangaInfo.categories.map { it.name }
            val tags = mangaInfo.tags.map { "#${it.name}" }

            val genreString = categories.plus(tags).joinToString(", ")
            genre = genreString
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")

        return GET("$baseUrl/api/app/works/$mangaId/episodes/v2?order=desc", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = json.decodeFromString<Chapters>(response.body.string())

        return chapters.data.episodes.mapIndexed { i, episodeInfo ->
            SChapter.create().apply {
                val episode = episodeInfo.episode
                if (episode == null) {
                    name = "※${episodeInfo.message}"
                    url = ""
                } else {
                    name = episode.numbering_title.plus(": ${episode.sub_title}")
                    url = "/viewer/stories/${episode.id}"
                    date_upload = episode.read_start_at
                }
                chapter_number = i.toFloat()
            }
        }
    }

    override fun fetchPageList(chapter: SChapter) =
        if (chapter.name.contains("※") && chapter.url.isEmpty()) {
            throw Error(chapter.name.substringAfter("※"))
        } else {
            super.fetchPageList(chapter)
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun pageListParse(response: Response): List<Page> {
        val episodeId = response.request.url.pathSegments.last()

        val handler = Handler(Looper.getMainLooper())
        val base64ImageStrings = mutableListOf<String>()

        val pageNumberLatch = CountDownLatch(1)
        var pageLoadingLatch: CountDownLatch? = null
        var pageRetrieveLatch: CountDownLatch? = null

        var webView: WebView? = null
        handler.post {
            webView = WebView(Injekt.get<Application>()).apply {
                settings.javaScriptEnabled = true

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        val url = request.url.toString()

                        if (url.endsWith("1648-75b5954031fcc879.js")) {
                            val js = deshuffleScript.byteInputStream()

                            return WebResourceResponse("application/javascript", "UTF-8", js)
                        }

                        if (url.endsWith("3681-984fc8a29466ea34.js")) {
                            val js = functionToChangeScript.byteInputStream()

                            return WebResourceResponse("application/javascript", "UTF-8", js)
                        }

                        return super.shouldInterceptRequest(view, request)
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    var pageNumberNotKnown = true
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        if (pageNumberNotKnown && consoleMessage.message().startsWith("pages number")) {
                            val pageNumber = consoleMessage.message()
                                .removePrefix("pages number: ").toInt()
                            pageLoadingLatch = CountDownLatch(pageNumber)
                            pageRetrieveLatch = CountDownLatch(pageNumber)

                            pageNumberNotKnown = false
                            pageNumberLatch.countDown()
                        }

                        if (consoleMessage.message().startsWith("image deshuffled")) {
                            pageLoadingLatch!!.countDown()
                            return super.onConsoleMessage(consoleMessage)
                        }

                        if (consoleMessage.message().startsWith("data:image/png;base64")) {
                            base64ImageStrings.add(consoleMessage.message())

                            pageRetrieveLatch!!.countDown()
                            return super.onConsoleMessage(consoleMessage)
                        }

                        return super.onConsoleMessage(consoleMessage)
                    }
                }

                loadUrl("https://comic.pixiv.net/viewer/stories/$episodeId")
            }
        }

        pageNumberLatch.await()
        pageLoadingLatch!!.await()
        handler.postDelayed({
            webView!!.evaluateJavascript(
                """
                var urls = [];
                var count = 0;

                do {
                  var element = document.querySelector("#page-" + count);
                  if (element !== null) {
                    var matches = element.getAttribute("style").match(/url\("([^"]+)"\)/);
                    var url = matches[1];
                    urls.push(url);
                  }
                  count++;
                } while (element !== null);

                urls;
                """.trimIndent(),
            ) { urls ->

                webView!!.evaluateJavascript(
                    """
                    async function a(urls) {
                        var b64s = [];
                        for(let i = 0; i < urls.length; i++) {
                            try {
                                const response = await fetch(urls[i]);
                                if (!response.ok) {
                                    throw new Error(`HTTP error!`);
                                }

                                const blob = await response.blob();
                                const b64 = await new Promise((resolve, reject) => {
                                    const reader = new FileReader();
                                    reader.onload = () => resolve(reader.result);
                                    reader.onerror = reject;
                                    reader.readAsDataURL(blob);
                                });
                                b64s.push(b64);
                            } catch (error) {
                                console.error("Error fetching Blob data:", error);
                                throw error;
                            }
                        }
                        return b64s;
                    }

                    (async () => {
                        try {
                            const result = await a($urls);
                            for(i = 0; i < result.length; i++) {
                                console.log(result[i]);
                            }

                        } catch (error) {
                            console.error("Error:", error);
                        }
                    })();
                    """.trimIndent(),
                ) {}
            }
        }, 1000,)

        pageRetrieveLatch!!.await()
        handler.post {
            webView!!.destroy()
        }

        return base64ImageStrings.mapIndexed { i, base64 ->
            Page(
                url = "$baseUrl/viewer/stories/$episodeId",
                imageUrl = "https://127.0.0.1/?image=$base64",
                index = i,
            )
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    companion object {
        private const val POPULAR_MANGA_COUNT_PER_PAGE = 30
        private const val CATEGORY_HEADER_TEXT = "Can only filter 1 type (category or tag) at a time"
        private const val TAG_HEADER_TEXT = "If this filter by tag is used, keep category at \n\"No Category\""
        private val categories = arrayOf(
            "No Category",
            "恋愛",
            "動物",
            "グルメ",
            "ファンタジー",
            "ホラー・ミステリー",
            "アクション",
            "エッセイ",
            "ギャグ・コメディ",
            "日常",
            "ヒューマンドラマ",
            "スポーツ",
            "お仕事",
            "BL",
            "TL",
            "百合",
            "pixivコミック限定",
            "映像化",
            "コミカライズ",
            "タテヨミ",
            "読み切り",
            "その他",
        )
    }

    private val functionToChangeScript by lazy {
        javaClass
            .getResource("/assets/3681-984fc8a29466ea34.js")!!
            .readText()
    }

    private val deshuffleScript by lazy {
        javaClass
            .getResource("/assets/1648-75b5954031fcc879.js")!!
            .readText()
    }
}
