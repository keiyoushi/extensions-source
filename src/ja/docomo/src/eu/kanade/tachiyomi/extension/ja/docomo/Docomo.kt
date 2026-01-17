package eu.kanade.tachiyomi.extension.ja.docomo

import eu.kanade.tachiyomi.lib.publus.Publus.Decoder
import eu.kanade.tachiyomi.lib.publus.Publus.PublusInterceptor
import eu.kanade.tachiyomi.lib.publus.Publus.generatePages
import eu.kanade.tachiyomi.lib.publus.PublusPage
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup.parseBodyFragment

class Docomo : HttpSource() {
    override val name = "Docomo"
    private val domain = "docomo.ne.jp"
    override val baseUrl = "https://dbook.$domain"
    override val lang = "ja"
    override val supportsLatest = false

    private val apiUrl = "https://dxp-system.$domain"
    private val sessionUrl = "https://rs4x.mw-pf.jp"

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(PublusInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/ranking/all/".toHttpUrl().newBuilder()
            .addQueryParameter("s", "daily")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".o-ranking-list__item").map {
            SManga.create().apply {
                val anchor = it.selectFirst("a[href*=/item/]")!!
                setUrlWithoutDomain(anchor.absUrl("href"))
                title = it.selectFirst(".m-basic-card__title")!!.text()
                thumbnail_url = it.selectFirst("img.cd-cover")?.absUrl("src")
            }
        }

        val nextBtn = document.selectFirst(".m-pager__next a")
        val hasNextPage = if (nextBtn != null) {
            true
        } else {
            val currentItem = document.selectFirst(".m-pager__list li.-current")
            val nextItem = currentItem?.nextElementSibling()
            nextItem?.selectFirst("a") != null
        }

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/".toHttpUrl().newBuilder()
            .addQueryParameter("p", page.toString())
            .addQueryParameter("q", query)
            .addQueryParameter("s", "sort_seriespop")
            .addQueryParameter("t", "2")
            .addQueryParameter("ss", "1")
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".o-card-list-light__item").map {
            SManga.create().apply {
                val anchor = it.selectFirst("a[href*=/item/]")!!
                setUrlWithoutDomain(anchor.absUrl("href"))
                title = it.selectFirst(".m-card-light__title")!!.text()
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

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.p-header__title")!!.text()
            author = document.select(".p-information__author-list li a").joinToString { it.text() }
            description = document.selectFirst(".o-product-information__summary-text")?.text()
            thumbnail_url = document.selectFirst(".p-cover__image img")?.absUrl("src")
            genre = document.select(".m-data-list__name:contains(ジャンル) + .m-data-list__data a")
                .joinToString { it.text() }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val seriesId = document.selectFirst("input#series_id")?.attr("value")
            ?: return emptyList()

        val chapters = mutableListOf<SChapter>()
        var page = 1
        var hasNext = true

        while (hasNext) {
            val url = "$apiUrl/element/seriesshelf/get_contents".toHttpUrl().newBuilder()
                .addQueryParameter("seriesId", seriesId)
                .addQueryParameter("order", "a")
                .addQueryParameter("page", page.toString())
                .build()

            val chapterJson = client.newCall(GET(url, headers)).execute()
            val chapterResponse = chapterJson.parseAs<ChaptersResponse>()
            val fragment = parseBodyFragment(chapterResponse.html)

            val items = fragment.select(".o-series-list__card-item")
            items.forEach {
                val productId = it.selectFirst(".o-series-list__card")?.attr("data-product_id")
                val title = it.selectFirst(".o-series-list__card-title")!!.text()

                val chapter = SChapter.create().apply {
                    name = title
                    val viewerUrl = "$baseUrl/view/".toHttpUrl().newBuilder()
                        .addQueryParameter("cid", productId)
                        .addQueryParameter("cti", title)
                        .addQueryParameter("cc", "0000")
                        .build()
                    setUrlWithoutDomain(viewerUrl.toString())
                }
                chapters.add(chapter)
            }

            hasNext = chapterResponse.hasNext
            page++
        }

        return chapters.reversed()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val sesid = document.selectFirst("#mwrs4b-params")?.attr("data-sesid")!!
        val cid = response.request.url.queryParameter("cid")!!
        val body = FormBody.Builder()
            .add("cid", cid)
            .add("sesid", sesid)
            .build()

        val cUrl = "$sessionUrl/responder/sessionValidate".toHttpUrl()
        val cRequest = POST(cUrl.toString(), headers, body)
        val cResponse = client.newCall(cRequest).execute()
        val cPhp = cResponse.parseAs<CPhpResponse>().url
        val configRequest = GET(cPhp + "configuration_pack.json", headers)
        val configResponse = client.newCall(configRequest).execute()
        val packData = configResponse.parseAs<ConfigPack>().data
        val result = Decoder(packData).decode()
        val rootJson = result.json.parseAs<Map<String, JsonElement>>()
        val configElement = rootJson["configuration"] ?: throw Exception("Configuration not found in decrypted JSON")
        val container = configElement.parseAs<PublusConfiguration>()

        val pageContent = container.contents.mapIndexed { index, contentEntry ->
            val pageJson = rootJson[contentEntry.file]
                ?: throw Exception("Page config not found for ${contentEntry.file}")

            val pageConfig = pageJson.toString().parseAs<PublusPageConfig>()
            val details = pageConfig.fileLinkInfo.pageLinkInfoList[0].page

            PublusPage(
                index = index,
                filename = contentEntry.file,
                no = details.no,
                ns = details.ns,
                ps = details.ps,
                rs = details.rs,
                blockWidth = details.blockWidth,
                blockHeight = details.blockHeight,
                width = details.size.width,
                height = details.size.height,
            )
        }

        return generatePages(pageContent, result.keys, cPhp)
    }

    override fun imageUrlParse(response: Response): String {
        return response.request.url.toString()
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
}
