package eu.kanade.tachiyomi.extension.zh.hikarinagi

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.getArray
import keiyoushi.utils.getBooleanOrNull
import keiyoushi.utils.getInt
import keiyoushi.utils.getLong
import keiyoushi.utils.getObject
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getString
import keiyoushi.utils.obj
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class Hikarinagi :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()
    private val isNovelMode get() = Preferences.isNovel(preferences)

    override fun getHomeUrl() = if (isNovelMode) "$baseUrl/light-novels" else "$baseUrl/mangas"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        Preferences.buildPreferences(screen.context, isNovelMode).forEach { screen.addPreference(it) }
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(NovelTextInterceptor()).addInterceptor(NovelImageInterceptor())

    companion object {
        const val IMAGE_BASR_URL = "https://imagesp.yurari.moe"
        val FILTER_PARAMS = arrayOf("sort", "region", "audience", "status", "decade", "magazine_id")

        private const val UNAVAILABLE_MESSAGE = "未收录本卷内容，暂无在线阅读"
    }

    private fun String?.ifNotBlank(action: (String) -> Unit) = this?.takeIf(String::isNotBlank)?.let(action)

    private fun browseUrl(page: Int, query: String?, filters: FilterList): HttpUrl {
        val url = "$baseUrl/api/pages/mangas/browse".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "24")
        query.ifNotBlank { url.addQueryParameter("search", it) }
        filters.forEachIndexed { i, filter -> filter.toString().ifNotBlank { url.addQueryParameter(FILTER_PARAMS[i], it) } }
        return url.build()
    }

    private fun novelBrowseUrl(page: Int, query: String?, filters: FilterList): HttpUrl {
        val url = "$baseUrl/api/pages/light-novels/browse".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "24")
        query.ifNotBlank { url.addQueryParameter("search", it) }
        if (Preferences.isReadableOnly(preferences)) url.addQueryParameter("readable", "1")
        filters.forEach { filter ->
            (filter as? UrlPartFilter)?.toUrlPart()?.let { url.addQueryParameter(it.first, it.second) }
        }
        return url.build()
    }

    private fun parseBrowse(response: Response): MangasPage {
        val list = response.parseAs<JsonObject>().getObject("list")
        val manga = list.getArray("items").map {
            if (isNovelMode) it.parseAs<NovelItem>().toSManga() else it.parseAs<MangaItem>().toSManga()
        }
        val hasNextPage = with(list.getObject("meta")) { getInt("page") < getInt("total_pages") }
        return MangasPage(manga, hasNextPage)
    }

    /** Browses either category; without [filters] the category's own sort filter is used. */
    private suspend fun browse(page: Int, query: String? = null, filters: FilterList? = null, sortIndex: Int = 0): MangasPage {
        val filterList = filters ?: FilterList(
            if (isNovelMode) NovelSortFilter(Filter.Sort.Selection(sortIndex, false)) else SortFilter(Filter.Sort.Selection(sortIndex, false)),
        )
        val url = if (isNovelMode) novelBrowseUrl(page, query, filterList) else browseUrl(page, query, filterList)
        return parseBrowse(client.get(url))
    }

    override suspend fun getPopularManga(page: Int): MangasPage = browse(page, sortIndex = 1)

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(page, sortIndex = 0)

    override fun getFilterList(data: JsonElement?) = if (isNovelMode) {
        FilterList(
            NovelSortFilter(),
            StatusFilter(),
            DecadeFilter(),
            NovelReadableFilter(),
        )
    } else {
        FilterList(
            SortFilter(),
            RegionFilter(),
            AudienceFilter(),
            StatusFilter(),
            DecadeFilter(),
            MagazineFilter(),
        )
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = browse(page, query, filters)

    override fun getMangaUrl(manga: SManga) = if (isNovelMode) {
        "$baseUrl/light-novels/${manga.url.removePrefix(Preferences.NOVEL_URL_PREFIX)}"
    } else {
        "$baseUrl/mangas/${manga.url}"
    }

    override fun getChapterUrl(chapter: SChapter) = if (isNovelMode) {
        "$baseUrl/light-novel-volumes/${chapter.url}/read"
    } else {
        "$baseUrl/mangas/${chapter.memo.getString("cid")}/read/${chapter.url}"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = if (isNovelMode) {
        // Volumes and details come from the same response, so both are always returned.
        val id = manga.url.removePrefix(Preferences.NOVEL_URL_PREFIX)
        val data = client.get("$baseUrl/api/pages/light-novels/$id").parseAs<NovelData>()
        val sManga = data.lightNovel.toSManga(data.people(), data.tags())
        val workTitle = data.lightNovel.name
        // Volumes without an EPUB are listed too, marked so the reader can refuse them.
        val sChapters = data.volumes.mapIndexed { index, volume -> volume.toSChapter(index + 1, workTitle) }.reversed()
        SMangaUpdate(sManga, sChapters)
    } else {
        val data = client.get("$baseUrl/api/pages/mangas/${manga.url}").parseAs<MangaData>()
        val sManga = data.manga.toSManga(data.people(), data.tags())
        val sChapters = data.chapters.map { it.toSChapter(manga.url, manga.memo.getLong("updateAt")) }
        SMangaUpdate(sManga, sChapters.reversed())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = if (isNovelMode) {
        getNovelPageList(chapter)
    } else {
        val response = client.get("$baseUrl/api/pages/mangas/reader/${chapter.memo.getString("cid")}/${chapter.url}", ensureSuccess = false)
        if (response.code == 401) throw Exception("请先在 WebView 中登录")
        val urls = response.parseAs<JsonObject>().getObject("manifest").getArray("pages").map { it.obj.getString("src") }
        List(urls.size) { Page(it, imageUrl = urls[it]) }
    }

    /**
     * Novel volumes are served as EPUB files behind a short lived signed URL that has to be
     * requested with the login session of the website.
     */
    private suspend fun getNovelPageList(chapter: SChapter): List<Page> {
        if (chapter.memo.getBooleanOrNull("unavailable") == true) throw Exception(UNAVAILABLE_MESSAGE)

        val body = buildJsonObject { put("volume_id", chapter.url.toInt()) }.toJsonRequestBody()
        val session = client.post("$baseUrl/api/v3/reader/sessions", body, ensureSuccess = false)
        if (session.code == 401) throw Exception("请先在 WebView 中登录")
        // READER_EPUB_NOT_AVAILABLE, e.g. when the volume lost its EPUB after the chapter list was cached.
        if (session.code == 404) throw Exception(UNAVAILABLE_MESSAGE)

        val epubUrl = session.parseAs<JsonObject>().getObject("data").getString("url")
        val chapters = client.get(epubUrl).use { readEpubChapters(it.body.byteStream()) }
        return buildPages(chapters)
    }

    private fun buildPages(chapters: List<EpubChapter>): List<Page> {
        val pages = mutableListOf<Page>()
        chapters.forEach { chapter ->
            var heading = chapter.title
            var topPadding = true
            val paragraphs = ArrayDeque<String>()

            fun flushText() {
                while (paragraphs.isNotEmpty()) {
                    val budget = PAGE_CHARS - if (heading.isEmpty()) 0 else HEADING_CHARS
                    val text = StringBuilder()
                    while (paragraphs.isNotEmpty()) {
                        val next = paragraphs.first()
                        if (text.isNotEmpty() && text.length + next.length > budget) break
                        if (text.isNotEmpty()) text.append('\n')
                        text.append(paragraphs.removeFirst())
                    }
                    pages.add(Page(pages.size, imageUrl = NovelTextInterceptor.createUrl(heading, text.toString(), topPadding)))
                    heading = ""
                    topPadding = false
                }
            }

            chapter.blocks.forEach { block ->
                when (block) {
                    is EpubBlock.Image -> {
                        flushText()
                        val url = NovelImageInterceptor.save(block.bytes, block.path.substringAfterLast('.', "jpg"))
                        pages.add(Page(pages.size, imageUrl = url))
                        topPadding = true
                    }

                    is EpubBlock.Text -> paragraphs.addAll(block.text.splitParagraph())
                }
            }
            flushText()
        }
        return pages
    }
}

/**
 * Characters a novel page image holds. Bigger pages mean fewer of them; the ceiling is what the
 * reader can decode at once, roughly a 1000 x 2900 px bitmap here.
 */
private const val PAGE_CHARS = 800

/** Room the first page of a chapter gives up for its heading. */
private const val HEADING_CHARS = 60

/** Keeps a single paragraph from producing a page taller than the reader can decode. */
private fun String.splitParagraph(): List<String> {
    // Some EPUBs bring their own paragraph indent; the renderer adds one of its own.
    val text = trim()
    return when {
        text.isEmpty() -> emptyList()
        text.length <= PAGE_CHARS -> listOf(text)
        else -> text.chunked(PAGE_CHARS)
    }
}
