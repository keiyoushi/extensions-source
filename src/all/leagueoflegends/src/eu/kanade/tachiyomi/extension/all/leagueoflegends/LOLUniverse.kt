package eu.kanade.tachiyomi.extension.all.leagueoflegends

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class LOLUniverse(
    private val siteLang: String,
    override val lang: String = siteLang.substring(0, 2),
) : HttpSource() {
    override val baseUrl = "$UNIVERSE_URL/$siteLang/comic/"

    override val name = "League of Legends"

    override val supportsLatest = false

    private val json by injectLazy<Json>()

    private val pageCache = mutableMapOf<String, List<Page>>()

    override fun headersBuilder() = super.headersBuilder()
        .set("Origin", UNIVERSE_URL).set("Referer", "$UNIVERSE_URL/")

    override fun popularMangaRequest(page: Int) =
        GET("$MEEPS_URL/$siteLang/comics/index.json", headers)

    override fun chapterListRequest(manga: SManga) =
        GET("$MEEPS_URL/$siteLang/comics/${manga.url}/index.json", headers)

    override fun pageListRequest(chapter: SChapter) =
        GET("$COMICS_URL/$siteLang/${chapter.url}/index.json", headers)

    override fun popularMangaParse(response: Response) =
        response.decode<LOLHub>().mapNotNull {
            SManga.create().apply {
                title = it.title ?: return@mapNotNull null
                url = it.toString()
                description = it.description!!.clean()
                thumbnail_url = it.background.toString()
                genre = it.subtitle ?: it.champions?.joinToString()
            }
        }.run { MangasPage(this, false) }

    override fun chapterListParse(response: Response) =
        response.decode<LOLIssues>().map {
            SChapter.create().apply {
                name = it.title!!
                url = it.toString()
                chapter_number = it.index ?: -1f
                fetchPageList()
            }
        }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        client.newCall(popularMangaRequest(page)).asObservableSuccess()
            .map { popularMangaParse(it).filter(query) }!!

    override fun fetchMangaDetails(manga: SManga) =
        Observable.just(manga.apply { initialized = true })!!

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        if ('/' !in manga.url) return super.fetchChapterList(manga)
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "One Shot"
            chapter_number = 0f
            fetchPageList()
        }
        return Observable.just(listOf(chapter))
    }

    override fun fetchPageList(chapter: SChapter) =
        Observable.just(pageCache[chapter.url].orEmpty())!!

    override fun getMangaUrl(manga: SManga) =
        "$UNIVERSE_URL/$siteLang/comic/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) =
        "$UNIVERSE_URL/$siteLang/comic/${chapter.url}"

    override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException("Not used")

    override fun mangaDetailsRequest(manga: SManga) =
        throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response) =
        throw UnsupportedOperationException("Not used")

    override fun searchMangaParse(response: Response) =
        throw UnsupportedOperationException("Not used")

    override fun mangaDetailsParse(response: Response) =
        throw UnsupportedOperationException("Not used")

    override fun pageListParse(response: Response) =
        throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException("Not used")

    private inline fun <reified T> Response.decode() =
        json.decodeFromString<T>(body.string())

    private fun String.clean() =
        replace("</p> ", "</p>").replace("</p>", "\n").replace("<p>", "")

    private fun SChapter.fetchPageList() {
        client.newCall(pageListRequest(this)).execute().decode<LOLPages>().let {
            // The chapter date is only available in the page list
            date_upload = isoDate.parse(it.date)?.time ?: 0L
            pageCache[url] = it.mapIndexed { idx, img ->
                Page(idx, "", img.toString())
            }
        }
    }

    private fun MangasPage.filter(query: String) = copy(
        mangas.filter {
            it.title.contains(query, true) ||
                it.genre?.contains(query, true) ?: false
        },
    )

    companion object {
        private const val UNIVERSE_URL = "https://universe.leagueoflegends.com"

        private const val MEEPS_URL = "https://universe-meeps.leagueoflegends.com/v1"

        private const val COMICS_URL = "https://universe-comics.leagueoflegends.com/comics"

        private val isoDate by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
        }
    }
}
