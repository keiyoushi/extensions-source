package eu.kanade.tachiyomi.extension.ja.ganma

import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Request
import okhttp3.Response
import rx.Observable

open class Ganma : HttpSource(), ConfigurableSource {
    override val id = sourceId
    override val name = sourceName
    override val lang = sourceLang
    override val versionId = sourceVersionId
    override val baseUrl = "https://ganma.jp"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder().add("X-From", baseUrl)

    override fun popularMangaRequest(page: Int) =
        when (page) {
            1 -> GET("$baseUrl/api/1.0/ranking", headers)
            else -> GET("$baseUrl/api/1.1/ranking?flag=Finish", headers)
        }

    override fun popularMangaParse(response: Response): MangasPage {
        val list: List<Magazine> = response.parseAs()
        return MangasPage(list.map { it.toSManga() }, false)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/api/2.2/top", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val list = response.parseAs<Top>().boxes.flatMap { it.panels }
            .filter { it.newestStoryItem != null }
            .sortedByDescending { it.newestStoryItem!!.release }
        return MangasPage(list.map { it.toSManga() }, false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val pageNumber = when (filters.size) {
            0 -> 1
            else -> (filters[0] as TypeFilter).state + 1
        }
        return fetchPopularManga(pageNumber).map { mangasPage ->
            MangasPage(mangasPage.mangas.filter { it.title.contains(query) }, false)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw UnsupportedOperationException("Not used.")

    override fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used.")

    // navigate Webview to web page
    override fun mangaDetailsRequest(manga: SManga) =
        GET("$baseUrl/${manga.url.alias()}", headers)

    protected open fun realMangaDetailsRequest(manga: SManga) =
        GET("$baseUrl/api/1.0/magazines/web/${manga.url.alias()}", headers)

    override fun chapterListRequest(manga: SManga) = realMangaDetailsRequest(manga)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(realMangaDetailsRequest(manga)).asObservableSuccess()
            .map { mangaDetailsParse(it) }

    override fun mangaDetailsParse(response: Response): SManga =
        response.parseAs<Magazine>().toSMangaDetails()

    protected open fun List<SChapter>.sortedDescending() = this.asReversed()

    override fun chapterListParse(response: Response): List<SChapter> =
        response.parseAs<Magazine>().getSChapterList().sortedDescending()

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        client.newCall(pageListRequest(chapter)).asObservable()
            .map { pageListParse(chapter, it) }

    override fun pageListRequest(chapter: SChapter) =
        GET("$baseUrl/api/1.0/magazines/web/${chapter.url.alias()}", headers)

    protected open fun pageListParse(chapter: SChapter, response: Response): List<Page> {
        val manga: Magazine = response.parseAs()
        val chapterId = chapter.url.substringAfter('/')
        return manga.items.find { it.id == chapterId }!!.toPageList()
    }

    final override fun pageListParse(response: Response): List<Page> =
        throw UnsupportedOperationException("Not used.")

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not used.")

    protected open class TypeFilter : Filter.Select<String>("Type", arrayOf("Popular", "Completed"))

    override fun getFilterList() = FilterList(TypeFilter())

    protected inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream<Result<T>>(it.body.byteStream()).root
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = METADATA_PREF
            title = "Metadata (Debug)"
            setDefaultValue("")
        }.let { screen.addPreference(it) }
    }
}
