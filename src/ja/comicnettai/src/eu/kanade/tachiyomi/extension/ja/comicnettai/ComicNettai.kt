package eu.kanade.tachiyomi.extension.ja.comicnettai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.publus.Publus.Decoder
import keiyoushi.lib.publus.Publus.PublusInterceptor
import keiyoushi.lib.publus.Publus.generatePages
import keiyoushi.lib.publus.PublusPage
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class ComicNettai : HttpSource() {
    override val name = "Comic Nettai"
    override val baseUrl = "https://www.comicnettai.com"
    override val lang = "ja"
    override val supportsLatest = false

    private val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.ROOT)

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(PublusInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".full--comic__list .full--comic__item").map {
            SManga.create().apply {
                title = it.selectFirst(".full--comic__title")!!.text()
                setUrlWithoutDomain(it.absUrl("href"))
                thumbnail_url = it.selectFirst("img.full--comic__thum")?.absUrl("data-src")
            }
        }
        val hasNextPage = document.selectFirst(".pagenation__item:not(.is-hidde) .pagenation__item__link--next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".detail--title")!!.text()
            author = document.select(".detail__author__item").joinToString { it.text() }
            description = document.selectFirst(".detail--discription")?.text()
            thumbnail_url = document.selectFirst(".detail-catch__img")?.absUrl("src")
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var document = response.asJsoup()

        while (true) {
            val pageChapters = document.select(".detail--product__list a.detail--product__item").map {
                SChapter.create().apply {
                    name = it.selectFirst(".detail--product__item__title")!!.text()
                    setUrlWithoutDomain(it.absUrl("href"))
                    date_upload = dateFormat.tryParse(it.selectFirst(".detail--product__item__sdate")?.text())
                }
            }
            chapters.addAll(pageChapters)

            val nextUrl = document.selectFirst(".pagenation__item__link--next")?.absUrl("href")
            if (nextUrl.isNullOrEmpty()) {
                break
            }

            val request = GET(nextUrl, headers)
            val nextResponse = client.newCall(request).execute()
            document = nextResponse.asJsoup()
        }
        return chapters
    }

    override fun pageListParse(response: Response): List<Page> {
        val cid = response.request.url.queryParameter("cid")
        val cUrl = "$baseUrl/api/viewer/c".toHttpUrl().newBuilder()
            .addQueryParameter("cid", cid)
            .build()

        val cRequest = GET(cUrl, headers)
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

    override fun imageUrlParse(response: Response): String = response.request.url.toString()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
}
