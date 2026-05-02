package eu.kanade.tachiyomi.extension.zh.jinmantiantang

import android.util.Base64
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.utils.getPreferences
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Jinmantiantang :
    HttpSource(),
    ConfigurableSource {

    override val lang: String = "zh"
    override val name: String = "禁漫天堂"
    override val supportsLatest: Boolean = true

    private val preferences = getPreferences { preferenceMigration() }

    override val baseUrl: String = "https://" + preferences.baseUrl

    private val updateUrlInterceptor = UpdateUrlInterceptor(preferences)

    // 处理URL请求
    override val client: OkHttpClient = network.cloudflareClient
        .newBuilder()
        // Add rate limit to fix manga thumbnail load failure
        .rateLimitHost(
            baseUrl.toHttpUrl(),
            preferences.getString(MAINSITE_RATELIMIT_PREF, MAINSITE_RATELIMIT_PREF_DEFAULT)!!.toInt(),
            preferences.getString(MAINSITE_RATELIMIT_PERIOD, MAINSITE_RATELIMIT_PERIOD_DEFAULT)!!.toLong(),
        )
        .apply { interceptors().add(0, updateUrlInterceptor) }
        .addInterceptor(ScrambledImageInterceptor)
        .build()

    // 添加额外的header增加规避Cloudflare可能性
    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .setRandomUserAgent()

    // 点击量排序(人气)
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/albums?o=mv&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.list-col > div.p-b-15:not([data-group])").map { element ->
            popularMangaFromElement(element)
        }.filterGenre()
        val hasNextPage = document.selectFirst("a.prevnext") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun List<SManga>.filterGenre(): List<SManga> {
        val removedGenres = preferences.getString(BLOCK_PREF, "")!!.substringBefore("//").trim()
        if (removedGenres.isEmpty()) return this
        val removedList = removedGenres.lowercase().split(' ')
        return this.filterNot { manga ->
            manga.genre.orEmpty().lowercase().split(", ").any { removedList.contains(it) }
        }
    }

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val children = element.children()
        if (children.isNotEmpty() && children[0].tagName() == "a") children.removeFirst()
        if (children.size >= 4) {
            title = children[1].text()
            children[0].selectFirst("a")?.attr("href")?.let { setUrlWithoutDomain(it) }
            val img = children[0].selectFirst("img")
            if (img != null) {
                thumbnail_url = img.extractThumbnailUrl().substringBeforeLast('?')
            }
            author = children[2].select("a").joinToString(", ") { it.text() }
            genre = children[3].select("a").joinToString(", ") { it.text() }
        }
    }

    // 最新排序
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/albums?o=mr&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // For JinmantiantangUrlActivity
    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/album/$id", headers)

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val sManga = mangaDetailsParse(response)
        sManga.url = "/album/$id/"
        return MangasPage(listOf(sManga), false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = if (query.startsWith(PREFIX_ID_SEARCH_NO_COLON, true) || query.toIntOrNull() != null) {
        val id = query.removePrefix(PREFIX_ID_SEARCH_NO_COLON).removePrefix(":")
        client.newCall(searchMangaByIdRequest(id))
            .asObservableSuccess()
            .map { response -> searchMangaByIdParse(response, id) }
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    // 查询信息
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var params = filters.filterIsInstance<UriPartFilter>().joinToString("") { it.toUriPart() }

        val url = if (query.isNotEmpty() && !query.contains("-")) {
            // 禁漫天堂特有搜索方式: A +B --> A and B, A B --> A or B
            var newQuery = query.replace("+", "%2B").replace(" ", "+")
            // remove illegal param
            params = params.substringAfter("?")
            if (params.contains("search_query")) {
                val keyword = params.substringBefore("&").substringAfter("=")
                newQuery = "$newQuery+%2B$keyword"
                params = params.substringAfter("&")
            }
            "$baseUrl/search/photos?search_query=$newQuery&page=$page&$params"
        } else {
            params = if (params.isEmpty()) "/albums?" else params
            if (query.isEmpty()) {
                "$baseUrl$params&page=$page"
            } else {
                // 在搜索栏的关键词前添加-号来实现对筛选结果的过滤, 像 "-YAOI -扶他 -毛絨絨 -獵奇", 注意此时搜索功能不可用.
                val removedGenres = query.split(" ").filter { it.startsWith("-") }.joinToString("+") { it.removePrefix("-") }
                "$baseUrl$params&page=$page&screen=$removedGenres"
            }
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // 漫画详情
    private fun mangaDetailsResolve(response: Response): Document {
        val document = response.asJsoup()
        val scripts =
            document.select("#wrapper > script:containsData(function base64DecodeUtf8):containsData(document.write(html))")

        for (script in scripts) {
            val jsCode = script.html().trim()

            jsCode.lines().forEach { line ->
                val trimmedLine = line.trim()
                // html = base64DecodeUtf8("...")
                if (trimmedLine.startsWith("const html") || trimmedLine.startsWith("let html") || trimmedLine.startsWith(
                        "var html",
                    )
                ) {
                    val start =
                        trimmedLine.indexOf("base64DecodeUtf8(\"") + "base64DecodeUtf8(\"".length
                    val end = trimmedLine.indexOf("\");", start)
                    if (start > 0 && end > start) {
                        val html = Base64.decode(trimmedLine.substring(start, end), Base64.DEFAULT)
                        document.body().append(String(html))
                    }
                }
            }
        }
        return document
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = mangaDetailsResolve(response)
        return mangaDetailsParse(document)
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")?.text() ?: ""
        // keep thumbnail_url same as the one in popularMangaFromElement()
        val img = document.selectFirst(".thumb-overlay > img")
        thumbnail_url = img?.extractThumbnailUrl()?.substringBeforeLast('.') + "_3x4.jpg"
        author = selectAuthor(document)
        genre = selectDetailsStatusAndGenre(document, 0).trim().split(" ").joinToString(", ")

        // When the index passed by the "selectDetailsStatusAndGenre(document: Document, index: Int)" index is 1,
        // it will definitely return a String type of 0, 1 or 2. This warning can be ignored
        status = selectDetailsStatusAndGenre(document, 1).trim().toIntOrNull() ?: 0
        description = document.selectFirst("#intro-block .p-t-5.p-b-5")?.text()?.substringAfter("敘述：")?.trim() ?: ""
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    private fun Element.extractThumbnailUrl(): String = when {
        hasAttr("data-original") -> attr("data-original")
        hasAttr("src") -> attr("src")
        hasAttr("data-cfsrc") -> attr("data-cfsrc")
        else -> ""
    }

    // 查询作者信息
    private fun selectAuthor(document: Document): String {
        val elements = document.select("div.panel-body div.tag-block")
        if (elements.size > 3) {
            return elements[3].select(".btn-primary").joinToString { it.text() }
        }
        return ""
    }

    // 查询漫画状态和类别信息
    private fun selectDetailsStatusAndGenre(document: Document, index: Int): String {
        var status = "0"
        var genre = ""
        val spanGenres = document.select("span[itemprop=genre] a")
        if (spanGenres.isEmpty()) {
            return if (index == 1) status else genre
        }
        val elements: Elements = document.selectFirst("span[itemprop=genre]")?.select("a") ?: return ""
        for (value in elements) {
            when (val vote: String = value.select("a").text()) {
                "連載中" -> status = "1"
                "完結" -> status = "2"
                else -> genre = "$genre$vote "
            }
        }
        return if (index == 1) status else genre
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        url = element.selectFirst("a")?.attr("href") ?: ""
        name = element.selectFirst("a li h3")?.ownText() ?: ""
        date_upload = dateFormat.tryParse(element.selectFirst("a li span.hidden-xs")?.text())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = mangaDetailsResolve(response)
        val elements = document.select("div[id=episode-block] a[href^=/photo/]")
        if (elements.isEmpty()) {
            val singleChapter = SChapter.create().apply {
                name = "单章节"
                url = document.selectFirst("#album_photo_cover > div.thumb-overlay > a")?.attr("href") ?: ""
                date_upload = dateFormat.tryParse(document.select("[itemprop=datePublished]").last()?.attr("content"))
            }
            return listOf(singleChapter)
        }
        return elements.map { chapterFromElement(it) }.reversed()
    }

    // 漫画图片信息
    override fun pageListParse(response: Response): List<Page> {
        tailrec fun internalParse(document: Document, pages: MutableList<Page>): List<Page> {
            val elements = document.select("div[class=center scramble-page spnotice_chk][id*=0]")
            for (element in elements) {
                val img = element.selectFirst("img") ?: continue
                val src = img.attr("src")
                val dataCfsrc = img.attr("data-cfsrc")
                val imageUrl = if (src.contains("blank.jpg") || dataCfsrc.contains("blank.jpg")) {
                    img.attr("data-original").substringBefore("?")
                } else {
                    src.substringBefore("?")
                }
                pages.add(Page(pages.size, imageUrl = imageUrl))
            }
            val next = document.selectFirst("a.prevnext")
            return if (next == null) {
                pages
            } else {
                internalParse(client.newCall(GET(next.attr("abs:href"), headers)).execute().asJsoup(), pages)
            }
        }

        return internalParse(response.asJsoup(), mutableListOf())
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters
    override fun getFilterList() = FilterList(
        CategoryGroup(),
        SortFilter(),
        TimeFilter(),
        TypeFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        getPreferenceList(screen.context, preferences, updateUrlInterceptor.isUpdated).forEach(screen::addPreference)
        screen.addRandomUAPreference()
    }

    companion object {
        private const val PREFIX_ID_SEARCH_NO_COLON = "JM"
        const val PREFIX_ID_SEARCH = "$PREFIX_ID_SEARCH_NO_COLON:"
    }
}
