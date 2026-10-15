package eu.kanade.tachiyomi.extension.ja.mangatoshokanz

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

@Source
abstract class MangaToshokanZ : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addNetworkInterceptor(::r18Interceptor)
        .addInterceptor(DescrambleInterceptor())

    // author/illustrator name might just show blank if language not set to japan
    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("cookie", "_LANG_=ja")

    private val xhrHeaders: Headers
        get() = headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

    private var isR18 = false

    private fun r18Interceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // open access to R18 section
        if (request.url.host == "r18.mangaz.com" && isR18.not()) {
            val url = "https://r18.mangaz.com/attention/r18/yes"

            val r18Request = Request.Builder()
                .url(url)
                .head()
                .build()

            isR18 = true
            client.newCall(r18Request).execute().close()
        }

        return chain.proceed(request)
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = client.get("$baseUrl/ranking/views").asJsoup().toMangas("ul.rankingListThum > li")
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("title/addpage_renewal")
            .addQueryParameter("type", "official")
            .addQueryParameter("sort", "new")
            .addQueryParameter("page", page.toString())
            .build()

        return client.get(url, xhrHeaders).asJsoup().toMangasPage()
    }

    private fun Document.toMangasPage(): MangasPage {
        val mangas = toMangas("body > li")
        return MangasPage(mangas, mangas.size == 50)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("title/addpage_renewal")
            .addQueryParameter("query", query)
            .addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is Category -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("category", categories[filter.state].lowercase())
                    }
                    if (filter.state == 5) {
                        url.host("r18.mangaz.com")
                    }
                }

                is Sort -> {
                    url.addQueryParameter("sort", sortBy[filter.state].lowercase())
                }

                else -> {}
            }
        }

        return client.get(url.build(), xhrHeaders).asJsoup().toMangasPage()
    }

    private fun Document.toMangas(selector: String): List<SManga> = select(selector).filterNot { li ->
        // discard manga that in the middle of asking for license progress, it can't be read
        li.selectFirst(".iconConsent") != null
    }.map { li ->
        SManga.create().apply {
            url = li.selectFirst("a[href*=/detail/]")!!.attr("href").substringAfterLast("/")
            title = li.selectFirst("h2, span.trim")!!.text()

            thumbnail_url = li.selectFirst("img")!!.attr("data-src").ifBlank {
                li.selectFirst("img")!!.attr("src")
            }
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(Category(), Sort())

    private class Category : Filter.Select<String>("Category", categories)

    private class Sort : Filter.Select<String>("Sort", sortBy)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) getMangaDetails(manga) else manga }
        val chapterList = async { if (fetchChapters) getChapterList(manga) else chapters }

        SMangaUpdate(details.await(), chapterList.await())
    }

    // in this manga details section we use book/detail/id since it have tags over series/detail/id
    override fun getMangaUrl(manga: SManga): String {
        // normally manga published by the website has the same id in it's series and book
        // example: https://www.mangaz.com/series/detail/202371 (series)
        //          https://www.mangaz.com/book/detail/202371 (book)
        // strangely manga published by registered user has different id in it's series and book
        // example: https://www.mangaz.com/series/detail/224931 (series)
        //          https://www.mangaz.com/book/detail/224932 (book)

        // so in here we want the id from the manga thumbnail url since it contain the book id
        // instead of manga url that contain series id which used for the chapter section later
        // example: https://www.mangaz.com/series/detail/224931 (manga url)
        //          https://books.j-comi.jp/Books/224/224932/thumb160_1713230205.jpg (thumbnail url)
        val bookId = manga.thumbnail_url!!.substringBeforeLast("/").substringAfterLast("/")
        return baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("book/detail")
            .addPathSegment(bookId)
            .build()
            .toString()
    }

    private suspend fun getMangaDetails(manga: SManga): SManga {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        // credit label is e.g. 作, 原作, 著 (writer) or 画, 作画, マンガ (artist)
        val (artists, authors) = document.select("span.bookinfoDetailAuthor:has(a)").partition { span ->
            span.ownText().let { it.contains("画") || it.contains("マンガ") }
        }

        return SManga.create().apply {
            url = manga.url
            title = manga.title
            thumbnail_url = manga.thumbnail_url
            author = authors.joinToString { it.selectFirst("a")!!.text() }.ifEmpty { null }
            artist = artists.joinToString { it.selectFirst("a")!!.text() }.ifEmpty { null }
            description = document.selectFirst(".wordbreak")?.text()
            genre = document.select("#tags li.tag a").joinToString { it.text() }
            status = SManga.UNKNOWN
        }
    }

    // we want series/detail/id over book/detail/id in here since book/detail/id have problem
    // where if the name of the chapter become too long the end become ellipsis (...)
    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val seriesUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("series/detail")
            .addPathSegment(manga.url)
            .build()

        val response = client.get(seriesUrl)
        val isBook = response.request.url.pathSegments.first() == "book"
        val document = response.asJsoup()

        // if it's single chapter, it will be redirected back to book/detail/id
        if (isBook) {
            return listOf(
                SChapter.create().apply {
                    name = document.selectFirst(".GA4_booktitle")!!.text()
                    url = document.baseUri().substringAfterLast("/")
                    chapter_number = 1f
                    date_upload = 0
                },
            )
        }

        // if it's multiple chapters
        return document.select(".itemList li").reversed().mapIndexed { i, li ->
            SChapter.create().apply {
                name = li.selectFirst(".title")!!.text()
                url = li.selectFirst("a")!!.attr("href").substringAfterLast("/")
                chapter_number = i.toFloat()
                date_upload = 0
            }
        }.reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String = virgoBuilder()
        .addPathSegment("view")
        .addPathSegment(chapter.url)
        .build()
        .toString()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val encodedDoc = client.get(getChapterUrl(chapter)).asJsoup().selectFirst("#doc")!!.text()
        val doc = Base64.decode(encodedDoc, Base64.DEFAULT).toString(Charsets.UTF_8).parseAs<ViewerDoc>()
        val dir = doc.location.base + doc.location.scrambleDir

        return doc.orders.sortedBy { it.no }.mapIndexed { i, order ->
            Page(i, imageUrl = "$dir/${order.name}#${order.scramble.encode()}")
        }
    }

    private fun virgoBuilder(): HttpUrl.Builder = baseUrl.toHttpUrl().newBuilder()
        .host("vw.mangaz.com")
        .addPathSegment("virgo")

    companion object {
        private val categories = arrayOf(
            "All",
            "Mens",
            "Womens",
            "TL",
            "BL",
            "R18",
        )
        private val sortBy = arrayOf(
            "Popular",
            "New",
        )
    }
}
