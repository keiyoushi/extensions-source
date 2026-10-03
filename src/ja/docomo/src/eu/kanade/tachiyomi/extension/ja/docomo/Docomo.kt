package eu.kanade.tachiyomi.extension.ja.docomo

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.publus.PublusContent
import keiyoushi.lib.publus.PublusInterceptor
import keiyoushi.lib.publus.fetchPages
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException

@Source
abstract class Docomo : KeiSource() {
    private val apiUrl get() = "https://dxp-system.docomo.ne.jp"
    private val sessionUrl get() = "https://rs4x.mw-pf.jp"

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(PublusInterceptor())
        addInterceptor {
            val response = it.proceed(it.request())
            if (response.code == 403 && response.request.url.pathSegments.last() == "configuration_pack.json") {
                throw IOException("Log in via WebView and purchase this product to read.")
            }
            response
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/ranking/all/".toHttpUrl().newBuilder()
            .addQueryParameter("s", "daily")
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url).toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search/".toHttpUrl().newBuilder()
            .addQueryParameter("p", page.toString())
            .addQueryParameter("q", query)
            .addQueryParameter("s", "sort_seriespop")
            .addQueryParameter("t", "2")
            .addQueryParameter("ss", "1")
            .build()
        return client.get(url).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val document = this.asJsoup()
        val mangas = document.select(".o-ranking-list__item, .o-card-list-light__item").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.selectFirst("a[href*=/item/]")!!.absUrl("href"))
                title = it.selectFirst(".m-basic-card__title, .m-card-light__title")!!.text()
                thumbnail_url = it.selectFirst("img.cd-cover")?.absUrl("src")
            }
        }

        val nextPager = document.selectFirst(".m-pager__next")
        val hasNextPage = if (nextPager != null && !nextPager.attr("style").contains("display")) {
            nextPager.selectFirst("a") != null
        } else {
            val currentItem = document.selectFirst(".m-pager__list li.-current")
            val nextItem = currentItem?.nextElementSibling()
            nextItem != null && nextItem.selectFirst("a") != null && !nextItem.attr("style").contains("display")
        }

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val details = SManga.create().apply {
            title = document.selectFirst("h1.p-header__title")!!.text()
            author = document.select(".p-information__author-list li a").joinToString { it.text() }
            description = document.selectFirst(".o-product-information__summary-text")?.textOrNull()
            thumbnail_url = document.selectFirst(".p-cover__image img")?.absUrl("src")
            genre = document.select(".m-data-list__name:contains(ジャンル) + .m-data-list__data a").joinToString { it.text() }
        }

        if (!fetchChapters) return SMangaUpdate(details, chapters)

        val seriesId = document.selectFirst("input#series_id")!!.attr("value")
        val chapterList = buildList {
            var page = 1
            do {
                val contentsUrl = "$apiUrl/element/seriesshelf/get_contents".toHttpUrl().newBuilder()
                    .addQueryParameter("seriesId", seriesId)
                    .addQueryParameter("order", "a")
                    .addQueryParameter("page", page.toString())
                    .build()
                val result = client.get(contentsUrl).parseAs<ChaptersResponse>()
                Jsoup.parseBodyFragment(result.html).select(".o-series-list__card-item").mapTo(this) {
                    SChapter.create().apply {
                        url = it.selectFirst(".o-series-list__card")!!.attr("data-product_id")
                        name = it.selectFirst(".o-series-list__card-title")!!.text()
                    }
                }
                page++
            } while (result.hasNext)
        }

        return SMangaUpdate(
            details,
            chapterList.reversed(),
        )
    }

    @Serializable
    class ChaptersResponse(
        val html: String,
        val hasNext: Boolean,
    )

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/view/".toHttpUrl().newBuilder()
        .addQueryParameter("cid", chapter.url)
        .addQueryParameter("cti", chapter.name)
        .addQueryParameter("cc", "0000")
        .build()
        .toString()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val sesid = client.get(getChapterUrl(chapter)).asJsoup().selectFirst("#mwrs4b-params")!!.attr("data-sesid")
        val body = FormBody.Builder()
            .add("cid", chapter.url)
            .add("sesid", sesid)
            .build()

        val contentUrl = client.post("$sessionUrl/responder/sessionValidate", body).parseAs<PublusContent>().url!!
        return client.fetchPages(contentUrl)
    }
}
