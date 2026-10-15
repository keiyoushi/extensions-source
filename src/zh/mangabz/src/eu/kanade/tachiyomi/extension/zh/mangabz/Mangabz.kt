package eu.kanade.tachiyomi.extension.zh.mangabz

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.unpacker.SubstringExtractor
import keiyoushi.lib.unpacker.Unpacker
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.jsoup.select.Evaluator

@Source
abstract class Mangabz :
    MangabzTheme(),
    ConfigurableSource {

    private val mirror: Mirror
        get() = when (baseUrl.toHttpUrl().host) {
            "mangabz.com" -> Mirror("mangabz.com", "bz/", "mangabz_lang")
            "xmanhua.com" -> Mirror("xmanhua.com", "xm/", "xmanhua_lang")
            "yymanhua.com" -> Mirror("yymanhua.com", "yy/", "yymanhua_lang")
            else -> throw Exception("Unsupported url: $baseUrl")
        }

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = addCookie { listOf(mirror.langCookie to preferences.lang) }
        .rateLimit(5)

    private val urlSuffix: String
        get() = mirror.urlSuffix

    override fun Headers.Builder.configureHeaders() = set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:143.0) Gecko/20100101 Firefox/143.0")

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

    private suspend fun getMangaList(url: String): MangasPage = parseMangaList(client.get(url).asJsoup()).apply {
        for (manga in mangas) manga.stripMirror()
    }

    override suspend fun getPopularManga(page: Int) = getMangaList("$baseUrl/manga-list-p$page/")

    override suspend fun getLatestUpdates(page: Int) = getMangaList("$baseUrl/manga-list-0-0-2-p$page/")

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (MIRRORS.none { it.domain == url.host }) return null
        val titleId = url.pathSegments[0]
        val mirrorPath = "$titleId/".toMirror()

        val document = client.get("$baseUrl/$mirrorPath").asJsoup()
        return SManga.create().apply {
            this.url = document.location().removePrefix(baseUrl)
            parseDetails(document)
        }.stripMirror()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isEmpty()) {
            val ids = parseFilterList(filters)
            if (ids.isEmpty()) return getPopularManga(page)

            return getMangaList("$baseUrl/manga-list-$ids-p$page/")
        }

        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("title", query)
            .addQueryParameter("page", page.toString())
            .build()
        return getMangaList(url.toString())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url.toMirror()).asJsoup()
        manga.parseDetails(document)
        return SMangaUpdate(manga, parseChapterList(document))
    }

    override fun parseDescription(element: Element, title: String, details: Elements): String {
        val text = element.ownText()
        val start = text.removePrefix("${title}漫画 ，").removePrefix("${title}漫畫 ，")
        val collapsed = element.selectFirst(Evaluator.Tag("span"))?.ownText()
            ?: return start
        return start + collapsed
    }

    override fun parseDate(listTitle: String) = parseDateInternal(listTitle.substringAfterLast(", "))

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.removePrefix("/m").removeSuffix("/")
        val pageCount = chapter.name.substringAfterLast('（').removeSuffix("P）").toInt()
        val prefix = "$baseUrl${chapter.url}chapterimage.ashx?cid=$chapterId&page="
        // 1 request returns 2 pages, or 15 if server cache is ready, so we manually cache them below
        return List(pageCount) { Page(it, "$prefix${it + 1}#$pageCount") }
    }

    // key is chapterId, value[0] is URL prefix, value[1..pageCount] are paths
    private val imageUrlCache = object : LinkedHashMap<Int, Array<String?>>() {
        // limit cache to 10 chapters
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Array<String?>>?) = size > 10
    }

    override suspend fun getImageUrl(page: Page): String {
        val url = page.url.toHttpUrl()

        var cache: Array<String?>? = null
        url.fragment?.run {
            val pageCount = toInt()
            val chapterId = url.queryParameter("cid")!!.toInt()
            val realCache = imageUrlCache.getOrPut(chapterId) { arrayOfNulls(pageCount + 1) }
            val path = realCache[page.index + 1]
            if (path != null) return realCache[0]!! + path
            cache = realCache
        }

        val script = client.get(page.url).use { Unpacker.unpack(it.body.string()) }
        val parser = SubstringExtractor(script)
        val prefix = parser.substringBetween("pix=\"", "\"")
        // 2 pages, or 15 if server cache is ready
        val paths = parser.substringBetween("[\"", "\"]").split("\",\"")
        val pageNumber = page.index + 1
        cache?.run {
            this[0] = prefix
            for ((offset, path) in paths.withIndex()) this[pageNumber + offset] = path
        }
        return prefix + paths[0]
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = parseCategories(client.get("$baseUrl/manga-list-p1/").asJsoup()).toJsonElement()

    override fun getFilterList(data: JsonElement?) = getFilterListInternal(data?.parseAs<List<CategoryData>>())

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        getPreferencesInternal(screen.context).forEach(screen::addPreference)
    }
}
