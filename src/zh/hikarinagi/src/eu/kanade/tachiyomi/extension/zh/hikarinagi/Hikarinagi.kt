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
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.getArray
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

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(TextInterceptor()).addInterceptor(NovelImageInterceptor())

    companion object {
        const val IMAGE_BASR_URL = "https://imagesp.yurari.moe"
        val FILTER_PARAMS = arrayOf("sort", "region", "audience", "status", "decade", "magazine_id")

        /** Characters of novel text rendered into a single page image. */
        private const val PAGE_CHARS = 1000
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

    private fun parseBrowse(response: Response, toSManga: (JsonElement) -> SManga): MangasPage {
        val list = response.parseAs<JsonObject>().getObject("list")
        val manga = list.getArray("items").map(toSManga)
        val hasNextPage = with(list.getObject("meta")) { getInt("page") < getInt("total_pages") }
        return MangasPage(manga, hasNextPage)
    }

    private fun parseMangaBrowse(response: Response) = parseBrowse(response) { it.parseAs<MangaItem>().toSManga() }

    private fun parseNovelBrowse(response: Response) = parseBrowse(response) { it.parseAs<NovelItem>().toSManga() }

    override suspend fun getPopularManga(page: Int): MangasPage = if (isNovelMode) {
        val filters = FilterList(NovelSortFilter(Filter.Sort.Selection(1, false)))
        parseNovelBrowse(client.get(novelBrowseUrl(page, null, filters)))
    } else {
        val filters = FilterList(SortFilter(Filter.Sort.Selection(1, false)))
        parseMangaBrowse(client.get(browseUrl(page, null, filters)))
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = if (isNovelMode) {
        val filters = FilterList(NovelSortFilter(Filter.Sort.Selection(0, false)))
        parseNovelBrowse(client.get(novelBrowseUrl(page, null, filters)))
    } else {
        val filters = FilterList(SortFilter(Filter.Sort.Selection(0, false)))
        parseMangaBrowse(client.get(browseUrl(page, null, filters)))
    }

    override fun getFilterList(data: JsonElement?) = if (isNovelMode) {
        FilterList(
            NovelSortFilter(),
            NovelStatusFilter(),
            NovelDecadeFilter(),
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

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = if (isNovelMode) {
        parseNovelBrowse(client.get(novelBrowseUrl(page, query, filters)))
    } else {
        parseMangaBrowse(client.get(browseUrl(page, query, filters)))
    }

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
        val sChapters = data.volumes.filter { it.readingAvailable }.map { it.toSChapter() }.reversed()
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
        val body = ReaderSessionRequest(volumeId = chapter.url.toInt()).toJsonRequestBody()
        val session = client.post("$baseUrl/api/v3/reader/sessions", body, ensureSuccess = false)
        if (session.code == 401) throw Exception("请先在 WebView 中登录")
        // READER_EPUB_NOT_AVAILABLE, e.g. when the volume lost its EPUB after the chapter list was cached.
        if (session.code == 404) throw Exception("本卷暂无在线正文")

        val epubUrl = session.parseAs<ReaderSessionResponse>().data.url
        val chapters = client.get(epubUrl).use { readEpubChapters(it.body.byteStream()) }
        return buildPages(chapters)
    }

    private fun buildPages(chapters: List<EpubChapter>): List<Page> {
        val pages = mutableListOf<Page>()
        chapters.forEach { chapter ->
            var index = 0
            var heading = chapter.title
            while (index < chapter.blocks.size) {
                when (val block = chapter.blocks[index]) {
                    is EpubBlock.Image -> {
                        val imageUrl = NovelImageInterceptor.save(block.bytes, block.path.substringAfterLast('.', "jpg"))
                        pages.add(Page(pages.size, imageUrl = imageUrl))
                        index++
                    }

                    is EpubBlock.Text -> {
                        val text = StringBuilder()
                        while (index < chapter.blocks.size) {
                            val next = chapter.blocks[index]
                            if (next !is EpubBlock.Text || (text.isNotEmpty() && text.length + next.text.length > PAGE_CHARS)) break
                            if (text.isNotEmpty()) text.append('\n')
                            text.append(next.text.escapeHtml())
                            index++
                        }
                        pages.add(Page(pages.size, imageUrl = TextInterceptorHelper.createUrl(heading, text.toString())))
                    }
                }
                heading = ""
            }
        }
        return pages
    }
}

private fun String.escapeHtml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
