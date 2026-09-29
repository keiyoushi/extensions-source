package eu.kanade.tachiyomi.extension.pt.lycantoons

import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

@Source
abstract class LycanToons : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    // =====================Popular=====================

    override suspend fun getPopularManga(page: Int): MangasPage = metricsRequest("popular", page).parseAs<PopularResponse>().toMangasPage()

    // =====================Latest=====================

    override suspend fun getLatestUpdates(page: Int): MangasPage = metricsRequest("recently-updated", page).parseAs<PopularResponse>().toMangasPage()

    // =====================Search=====================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        var search = query
        val tags = filters.selectedTags().toMutableList()

        val genreEntry = tagMapping.entries.find { it.value.equals(query, ignoreCase = true) }
        if (genreEntry != null) {
            tags.add(genreEntry.key)
            search = ""
        }

        val payload = SearchRequestBody(
            limit = PAGE_LIMIT,
            page = page,
            search = search,
            seriesType = filters.valueOrEmpty<SeriesTypeFilter>(),
            status = filters.valueOrEmpty<StatusFilter>(),
            tags = tags.distinct(),
        )

        return webFetch("$baseUrl/api/series", payload.toJsonString())
            .parseAs<SearchResponse>()
            .toMangasPage()
    }

    override fun getFilterList(data: JsonElement?): FilterList = LycanToonsFilters.get()

    // =====================Details=====================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.slug()

        val details = async {
            if (fetchDetails) {
                seriesPage("$baseUrl/series/$slug").extractNextJs<SeriesDto>()!!.toSManga()
            } else {
                manga
            }
        }

        val chapterList = async {
            if (fetchChapters) {
                seriesPage("$baseUrl/series/$slug/1").extractNextJs<ChapterResponse>()?.capitulos!!
                    .map { it.toSChapter(slug) }
                    .sortedByDescending { it.chapter_number }
            } else {
                chapters
            }
        }

        SMangaUpdate(details.await(), chapterList.await())
    }

    // =====================Pages========================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = seriesPage("$baseUrl${chapter.url}").extractNextJs<ChapterIdDto>()!!.capituloId

        return webFetch("$baseUrl/api/chapters/$chapterId/view-pages")
            .parseAs<PageList>()
            .pages
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    // =====================Utils=====================

    // Cloudflare challenges API requests that do not come from a browser, so fetch them from a same-origin page
    private suspend fun webFetch(url: String, jsonBody: String? = null): String = runWebView {
        userAgent = headers["User-Agent"]!!
        jsBridge("lycanResult") { resolve(it) }
        jsBridge("lycanError") { reject(IOException(it)) }
        onPageFinished {
            val init = jsonBody?.let { "{method:'POST',headers:{'Content-Type':'application/json'},body:${it.toJsonString()}}" } ?: "{}"
            evaluateJs(
                """
                fetch(${url.toJsonString()}, $init)
                    .then(r => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.text(); })
                    .then(t => lycanResult.post(t))
                    .catch(e => lycanError.post(e.message));
                """.trimIndent(),
            )
        }
        loadData("$baseUrl/", " ")
    }

    private suspend fun metricsRequest(path: String, page: Int): String = webFetch("$baseUrl/api/metrics/$path?limit=$PAGE_LIMIT&page=$page")

    private fun SManga.slug(): String = url.substringBefore("?").substringAfterLast("/")

    // Next.js RSC fetches get challenged too, so load the page itself as a document and skip its subresources
    private suspend fun seriesPage(url: String): Document = runWebView<String> {
        userAgent = headers["User-Agent"]!!
        interceptRequest { if (it.isForMainFrame) null else WebResourceResponse(null, null, null) }
        onPageFinished {
            evaluateJs("document.documentElement.outerHTML") { resolve(it.parseAs()) }
        }
        loadUrl(url.substringBefore("?"))
    }.let { Jsoup.parse(it, url) }

    companion object {
        private const val PAGE_LIMIT = 20
    }
}
