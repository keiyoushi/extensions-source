package eu.kanade.tachiyomi.extension.ja.mangaparkpublisher

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class MangaParkPublisher : HttpSource() {
    override val name = "Manga-Park"
    override val baseUrl = "https://manga-park.com"
    override val lang = "ja"
    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("yyyy/M/d", Locale.ROOT)
    private val apiUrl = "$baseUrl/api/chapter"

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            if (response.code == 401 && request.url.pathSegments.contains("api")) {
                throw IOException("Log in via WebView and purchase this chapter.")
            }

            response
        }
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ranking?target=all", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.list div.rankingHome ul.common-list li a, div.list div.series div.titles li a, div.list div.search-result ul.common-list li a").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                title = it.selectFirst("div.info h3")!!.text()
                thumbnail_url = it.selectFirst("div.thumb > img")!!.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/series", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filter = filters.firstInstance<TypeFilter>()
        val url = baseUrl.toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            url.addPathSegment("search")
            url.addPathSegment("freeword")
            url.addQueryParameter("key", query)
            return GET(url.build(), headers)
        }

        if (filter.type == "ranking") {
            url.addPathSegment("ranking")
            url.addQueryParameter("target", filter.value)
        } else {
            url.addPathSegment("series")
            url.addPathSegment(filter.value)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val titleInfo = document.selectFirst("div.titleMain div.titleInfo")!!
        return SManga.create().apply {
            title = titleInfo.selectFirst("h1")!!.text()
            author = titleInfo.selectFirst("p.author")?.text()
            description = document.selectFirst("p.explanation")?.text()
            thumbnail_url = document.selectFirst("div.titleThumb img")?.absUrl("src")
            genre = titleInfo.select("div.titleCategory ul li a").joinToString { it.text() }
            val statusText = titleInfo.selectFirst("div.tag ul li a")?.text()
            status = when {
                statusText?.contains("完結") == true -> SManga.COMPLETED
                statusText?.contains("更新") == true -> SManga.ONGOING
                statusText?.contains("休載中") == true -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.chapter ul li[data-chapter-id]").map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.attr("data-chapter-id"))
                val title = it.selectFirst("p.chapterTitle")!!.text()
                val isFree = it.selectFirst("div.free-badge img") != null
                name = if (isFree) "\uD83C\uDD93 $title" else title
                date_upload = dateFormat.tryParse(it.selectFirst("div.date span")!!.text())
                chapter_number = it.attr("data-chapter-name").toFloat()
            }
        }.reversed()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = "$apiUrl/${chapter.url}".toHttpUrl().newBuilder().build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapters = try {
            response.parseAs<ApiResponse>().data.chapter
        } catch (_: Exception) {
            throw Exception("You need to purchase this chapter.")
        }

        val images = chapters.flatMap { it.images }.map { image ->
            image.path.toHttpUrl().newBuilder()
                .fragment(image.key)
                .build()
                .toString()
        }

        return images.mapIndexed { i, url ->
            Page(i, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        TypeFilter(),
    )

    private class TypeFilter :
        SelectFilter(
            "Filter by",
            arrayOf(
                Triple("(ランキング) 総合", "ranking", "all"),
                Triple("(ランキング) 女性", "ranking", "woman"),
                Triple("(ランキング) 男性", "ranking", "man"),
                Triple("(ランキング) 大人向け", "ranking", "adult"),
                Triple("月曜日", "series", "mon"),
                Triple("火曜日", "series", "tue"),
                Triple("水曜日", "series", "wed"),
                Triple("木曜日", "series", "thu"),
                Triple("金曜日", "series", "fri"),
                Triple("土曜日", "series", "sat"),
                Triple("日曜日", "series", "sun"),
                Triple("完", "series", "end"),
            ),
        )

    private open class SelectFilter(displayName: String, private val vals: Array<Triple<String, String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        val type: String
            get() = vals[state].second

        val value: String
            get() = vals[state].third
    }
}
