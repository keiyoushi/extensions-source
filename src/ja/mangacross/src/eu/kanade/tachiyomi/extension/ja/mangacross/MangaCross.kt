package eu.kanade.tachiyomi.extension.ja.mangacross

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import kotlin.concurrent.thread

class MangaCross : HttpSource() {
    override val name = "Manga Cross"
    override val lang = "ja"
    override val baseUrl = "https://mangacross.jp"
    override val supportsLatest = true

    private val json: Json by injectLazy()

    // Pagination does not work. 9999 is a dummy large number.
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/comics.json?count=9999", headers)
    override fun popularMangaParse(response: Response) =
        MangasPage(response.parseAs<MCComicList>().comics.map { it.toSManga() }, false)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/api/episodes.json?page=$page", headers)
    override fun latestUpdatesParse(response: Response): MangasPage {
        val result: MCEpisodeList = response.parseAs()
        return MangasPage(result.episodes.map { it.comic!!.toSManga() }, result.current_page < result.total_pages)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        if (query.isNotEmpty()) {
            GET("$baseUrl/api/comics/keywords/$query.json", headers)
        } else {
            when (val tag = filters.filterIsInstance<TagFilter>().firstOrNull()?.getTag()) {
                null -> popularMangaRequest(page)
                is MCComicCategory -> GET("$baseUrl/api/comics/categories/${tag.name}.json", headers)
                is MCComicGenre -> GET("$baseUrl/api/comics/tags/${tag.name}.json", headers)
            }
        }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(chapterListRequest(manga)).asObservableSuccess()
            .map { mangaDetailsParse(it).apply { initialized = true } }

    // mangaDetailsRequest untouched in order to let WebView open web page instead of json

    override fun mangaDetailsParse(response: Response) = response.parseAs<MCComicDetails>().comic.toSManga()

    override fun chapterListRequest(manga: SManga) = GET("$baseUrl/api${manga.url}.json", headers)

    override fun chapterListParse(response: Response) = response.parseAs<MCComicDetails>().comic.toSChapterList()

    override fun pageListParse(response: Response): List<Page> {
        return try {
            response.parseAs<MCViewer>().episode_pages.mapIndexed { i, it ->
                Page(i, imageUrl = it.image.original_url)
            }
        } catch (e: SerializationException) {
            throw Exception("Chapter is no longer available!")
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Not used.")

    private lateinit var tags: List<Pair<String, MCComicTag?>>
    private var isFetchingTags = false

    private fun fetchTags() {
        if (isFetchingTags) return
        isFetchingTags = true
        thread {
            try {
                val response = client.newCall(GET("$baseUrl/api/menus.json", headers)).execute()
                val filterList = response.parseAs<MCMenu>().toFilterList()
                tags = listOf(Pair("All", null)) + filterList
            } catch (e: Exception) {
                Log.e("MangaCross", "Failed to fetch filters ($e)")
            } finally {
                isFetchingTags = false
            }
        }
    }

    override fun getFilterList() =
        if (::tags.isInitialized) {
            FilterList(
                Filter.Header("NOTE: Ignored if using text search!"),
                TagFilter("Tag", tags),
            )
        } else {
            fetchTags()
            FilterList(
                Filter.Header("Fetching tags..."),
                Filter.Header("Go back to previous screen and retry."),
            )
        }

    private class TagFilter(name: String, private val tags: List<Pair<String, MCComicTag?>>) :
        Filter.Select<String>(name, tags.map { it.first }.toTypedArray()) {
        fun getTag() = tags[state].second
    }

    private inline fun <reified T> Response.parseAs(): T = json.decodeFromStream(this.body.byteStream())
}
