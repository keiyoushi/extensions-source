package eu.kanade.tachiyomi.extension.tr.araznovel

import android.util.Base64
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.string
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Duration.Companion.seconds

@Source
abstract class ArazNovel : KeiSource() {

    private val apiUrl get() = "$baseUrl/wp-json/aotori/v1/serie"

    override suspend fun getPopularManga(page: Int) = browse(page, "views")

    override suspend fun getLatestUpdates(page: Int) = browse(page, "latest")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return browse(page, filters.firstInstance<SortFilter>().value)

        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .build()
        val mangas = client.get(url).parseAs<List<SerieDto>>().map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    private suspend fun browse(page: Int, orderBy: String): MangasPage {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("orderby", orderBy)
            .addQueryParameter("page", page.toString())
            .build()
        val result = client.get(url).parseAs<SerieListDto>()
        return MangasPage(result.items.map { it.toSManga() }, result.hasMore)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Arama yaparken sıralama yok sayılır"),
        SortFilter(),
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter(String::isNotBlank)
        if (segments.size < 2 || segments[0] != "series") return null
        val wpUrl = "$baseUrl/wp-json/wp/v2/manga".toHttpUrl().newBuilder()
            .addQueryParameter("slug", segments[1])
            .addQueryParameter("_fields", "id")
            .build()
        val id = client.get(wpUrl).parseAs<List<WpMangaDto>>().firstOrNull()?.id ?: return null
        return client.get("$apiUrl/$id").parseAs<SerieDetailsDto>().toSManga()
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/series/${manga.memo["slug"]!!.string}/"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/series/${chapter.memo["series"]!!.string}/${chapter.memo["slug"]!!.string}/"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val serie = client.get("$apiUrl/${manga.url}").parseAs<SerieDetailsDto>()
        return SMangaUpdate(
            serie.toSManga(),
            serie.chapters.map { it.toSChapter(serie.slug) },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        var document = client.get(chapterUrl).asJsoup()
        if (document.selectFirst(".cf-turnstile, .g-recaptcha") != null) {
            document = passGate(chapterUrl).asJsoup(chapterUrl)
        }
        // data-src is base64 of an expiring proxy URL; its `u` param is the base64 of the plain image URL
        return document.select(".ao-reader-images__img[data-src]").mapIndexed { i, element ->
            val proxyUrl = String(Base64.decode(element.attr("data-src"), Base64.DEFAULT)).toHttpUrl()
            val imageUrl = String(Base64.decode(proxyUrl.queryParameter("u")!!, Base64.DEFAULT))
            Page(i, imageUrl = imageUrl)
        }
    }

    // The site's own Turnstile gate passes without interaction in a WebView and sets a cookie shared with the client
    private suspend fun passGate(url: String): String = try {
        runWebView(timeout = 60.seconds) {
            poll(1.seconds) {
                evaluateJs("document.querySelector('.ao-reader-images') ? document.documentElement.outerHTML : null") { value ->
                    value.parseAs<String?>()?.let { resolve(it) }
                }
            }
            loadUrl(url)
        }
    } catch (_: WebViewTimeoutException) {
        throw Exception("İnsan doğrulaması geçilemedi, bölümü WebView'de açın")
    }

    private class SortFilter :
        Filter.Select<String>(
            "Sırala",
            SORTS.map { it.first }.toTypedArray(),
        ) {
        val value get() = SORTS[state].second
    }

    companion object {
        private val SORTS = listOf(
            "Son güncellenen" to "latest",
            "En yeni" to "new",
            "Popüler" to "views",
            "Puan" to "rating",
        )
    }
}
