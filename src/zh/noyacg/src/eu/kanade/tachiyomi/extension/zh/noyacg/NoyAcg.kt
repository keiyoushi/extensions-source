package eu.kanade.tachiyomi.extension.zh.noyacg

import android.app.Application
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class NoyAcg : HttpSource(), ConfigurableSource {
    override val name get() = "NoyAcg"
    override val lang get() = "zh"
    override val supportsLatest get() = true
    override val baseUrl get() = "https://noy1.top"

    private val imageCdn by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000).imageCdn
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val body = FormBody.Builder()
            .addEncoded("page", page.toString())
            .addEncoded("type", "day")
            .build()
        return POST("$baseUrl/api/readLeaderboard", headers, body)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val page = (response.request.body as FormBody).encodedValue(0).toInt()
        val imageCdn = imageCdn
        val listingPage: ListingPageDto = response.parseAs()
        val entries = listingPage.entries.map { it.toSManga(imageCdn) }
        val hasNextPage = page * LISTING_PAGE_SIZE < listingPage.len
        return MangasPage(entries, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val body = FormBody.Builder()
            .addEncoded("page", page.toString())
            .build()
        return POST("$baseUrl/api/booklist_v2", headers, body)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun getFilterList() = getFilterListInternal()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filters = filters.ifEmpty { getFilterListInternal() }
        val builder = FormBody.Builder()
            .addEncoded("page", page.toString())
        return if (query.isNotBlank()) {
            builder.add("info", query)
            for (filter in filters) if (filter is SearchFilter) filter.addTo(builder)
            POST("$baseUrl/api/search_v2", headers, builder.build())
        } else {
            var path: String? = null
            for (filter in filters) when (filter) {
                is RankingFilter -> path = filter.path
                is RankingRangeFilter -> filter.addTo(builder)
                else -> {}
            }
            POST("$baseUrl/api/${path!!}", headers, builder.build())
        }
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // for WebView
    override fun mangaDetailsRequest(manga: SManga) = GET("$baseUrl/#/book/${manga.url}")

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val body = FormBody.Builder()
            .addEncoded("bid", manga.url)
            .build()
        val request = POST("$baseUrl/api/getbookinfo", headers, body)
        return client.newCall(request).asObservableSuccess().map {
            it.parseAs<MangaDto>().toSManga(imageCdn)
        }
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val pageCount = manga.pageCount
        if (pageCount <= 0) return Observable.just(emptyList())
        val chapter = SChapter.create().apply {
            url = "${manga.url}#$pageCount"
            name = "单章节"
            date_upload = manga.timestamp
            chapter_number = -2f
        }
        return Observable.just(listOf(chapter))
    }

    // for WebView
    override fun pageListRequest(chapter: SChapter) = GET("$baseUrl/#/read/" + chapter.url.substringBefore('#'))

    override fun pageListParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val mangaId = chapter.url.substringBefore('#')
        val pageCount = chapter.url.substringAfter('#').toInt()
        val imageCdn = imageCdn
        val pageList = List(pageCount) {
            Page(it, imageUrl = "$imageCdn/$mangaId/${it + 1}.webp")
        }
        return Observable.just(pageList)
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private val json: Json by injectLazy()

    private inline fun <reified T> Response.parseAs(): T = try {
        json.decodeFromStream(body.byteStream())
    } catch (e: Throwable) {
        throw Exception("请在 WebView 中登录")
    } finally {
        close()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        getPreferencesInternal(screen.context).forEach(screen::addPreference)
    }
}
