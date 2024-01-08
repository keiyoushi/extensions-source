package eu.kanade.tachiyomi.extension.zh.mangabz

import android.app.Application
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.unpacker.SubstringExtractor
import eu.kanade.tachiyomi.lib.unpacker.Unpacker
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.jsoup.select.Evaluator
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Mangabz : MangabzTheme("Mangabz"), ConfigurableSource {

    override val baseUrl: String
    override val client: OkHttpClient

    private val urlSuffix: String

    init {
        val preferences = Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
        val mirror = preferences.mirror
        baseUrl = "https://" + mirror.domain
        urlSuffix = mirror.urlSuffix

        val cookieInterceptor = CookieInterceptor(mirror.domain, mirror.langCookie, preferences.lang)
        client = network.client.newBuilder()
            .rateLimit(5)
            .addNetworkInterceptor(cookieInterceptor)
            .build()
    }

    private fun SManga.stripMirror() = apply {
        val old = url
        url = buildString(old.length) {
            append(old, 0, old.length - urlSuffix.length).append("bz/")
        }
    }

    private fun String.toMirror() = buildString {
        val old = this@toMirror // ...bz/
        append(old, 0, old.length - 3).append(urlSuffix)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.isEmpty()) {
            val ids = parseFilterList(filters)
            if (ids.isEmpty()) return fetchPopularManga(page)

            return client.newCall(GET("$baseUrl/manga-list-$ids-p$page/", headers))
                .asObservableSuccess().map(::searchMangaParse)
        }

        val path = when {
            query.startsWith(PREFIX_ID_SEARCH) -> query.removePrefix(PREFIX_ID_SEARCH)
            query.startsWith(baseUrl) -> query.removePrefix(baseUrl).trim('/')
            else -> return super.fetchSearchManga(page, query, filters)
        }
        val mirrorPath = "$path/".toMirror()

        return client.newCall(GET("$baseUrl/$mirrorPath", headers))
            .asObservableSuccess().map { MangasPage(listOf(mangaDetailsParse(it)), false) }
    }

    override fun searchMangaParse(response: Response) = super.searchMangaParse(response).apply {
        for (manga in mangas) manga.stripMirror()
    }

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url.toMirror(), headers)
    override fun mangaDetailsParse(response: Response) = super.mangaDetailsParse(response).stripMirror()

    override fun parseDescription(element: Element, title: String, details: Elements): String {
        val text = element.ownText()
        val start = if (text.startsWith(title)) title.length + 4 else 0
        val collapsed = element.selectFirst(Evaluator.Tag("span"))?.ownText()
            ?: return text.substring(start)
        return buildString { append(text, start, text.length - 1).append(collapsed) }
    }

    override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url.toMirror(), headers)

    override fun parseDate(listTitle: String) = parseDateInternal(listTitle.substringAfterLast(", "))

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val chapterId = chapter.url.removePrefix("/m").removeSuffix("/")
        val pageCount = chapter.name.substringAfterLast('（').removeSuffix("P）").toInt()
        val prefix = "$baseUrl${chapter.url}chapterimage.ashx?cid=$chapterId&page="
        // 1 request returns 2 pages, or 15 if server cache is ready, so we manually cache them below
        val list = List(pageCount) { Page(it, "$prefix${it + 1}#$pageCount") }
        return Observable.just(list)
    }

    override fun pageListParse(response: Response) = throw UnsupportedOperationException()

    // key is chapterId, value[0] is URL prefix, value[1..pageCount] are paths
    private val imageUrlCache = object : LinkedHashMap<Int, Array<String?>>() {
        // limit cache to 10 chapters
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Array<String?>>?) = size > 10
    }

    override fun fetchImageUrl(page: Page): Observable<String> {
        val url = page.url.toHttpUrl()

        var cache: Array<String?>? = null
        url.fragment?.run {
            val pageCount = toInt()
            val chapterId = url.queryParameter("cid")!!.toInt()
            val realCache = imageUrlCache.getOrPut(chapterId) { arrayOfNulls(pageCount + 1) }
            val path = realCache[page.index + 1]
            if (path != null) return Observable.just(realCache[0]!! + path)
            cache = realCache
        }

        return client.newCall(GET(page.url, headers)).asObservableSuccess().map {
            val script = Unpacker.unpack(it.body.string())
            val parser = SubstringExtractor(script)
            val prefix = parser.substringBetween("pix=\"", "\"")
            // 2 pages, or 15 if server cache is ready
            val paths = parser.substringBetween("[\"", "\"]").split("\",\"")
            val pageNumber = page.index + 1
            cache?.run {
                this[0] = prefix
                for ((offset, path) in paths.withIndex()) this[pageNumber + offset] = path
            }
            prefix + paths[0]
        }
    }

    var categories = emptyList<CategoryData>()

    override fun parseFilters(document: Document) {
        if (categories.isEmpty()) categories = parseCategories(document)
    }

    override fun getFilterList() = getFilterListInternal(categories)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        getPreferencesInternal(screen.context).forEach(screen::addPreference)
    }

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }
}
