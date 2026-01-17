package eu.kanade.tachiyomi.extension.ja.comicboost

import eu.kanade.tachiyomi.lib.publus.Publus.Decoder
import eu.kanade.tachiyomi.lib.publus.Publus.PublusInterceptor
import eu.kanade.tachiyomi.lib.publus.Publus.generatePages
import eu.kanade.tachiyomi.lib.publus.PublusPage
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class ComicBoost : HttpSource() {
    override val name = "Comic Boost"
    override val baseUrl = "https://comic-boost.com"
    override val lang = "ja"
    override val supportsLatest = false

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.ROOT)

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(PublusInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/genre/".toHttpUrl().newBuilder()
            .addQueryParameter("p", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".book-list-item").map {
            SManga.create().apply {
                val thumbWrapper = it.selectFirst(".book-list-item-thum-wrapper")!!
                setUrlWithoutDomain(thumbWrapper.absUrl("href"))
                title = it.selectFirst(".title")!!.text()
                thumbnail_url = it.selectFirst("img.thum")?.absUrl("data-src")
            }
        }
        val hasNextPage = document.selectFirst(".pagination-list.right .to-next:not(.disabled) a") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("k", query)
            .addQueryParameter("p", page.toString())

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.comic-title")!!.text()
            author = document.select(".comic-main-right .author-list .author").joinToString {
                it.text().replace(Regex("^(原作|漫画|作画|キャラクター原案|原案)："), "")
            }
            description = document.selectFirst(".comic-description-text")?.text()
            genre = document.select(".tag-list .tag").joinToString { it.text() }
            thumbnail_url = document.selectFirst(".comic-main-thum-wrapper img")?.absUrl("src")
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var document = response.asJsoup()
        while (true) {
            val pageChapters = document.select(".book-product-list-item").map { element ->
                SChapter.create().apply {
                    val title = element.selectFirst(".title")!!.text()
                    val isPaid = element.selectFirst(".coin") != null
                    name = if (isPaid) "🔒 $title" else title
                    setUrlWithoutDomain(element.absUrl("href"))
                    date_upload = dateFormat.tryParse(element.selectFirst(".update-date")?.text())
                }
            }
            chapters.addAll(pageChapters)

            val nextBtn = document.selectFirst(".pagination-list.right .to-next:not(.disabled) a")
            val nextUrl = nextBtn?.attr("href")

            if (nextUrl.isNullOrEmpty()) {
                break
            }

            val request = GET(baseUrl + nextUrl, headers)
            val nextResponse = client.newCall(request).execute()
            document = nextResponse.asJsoup()
        }
        return chapters
    }

    override fun pageListParse(response: Response): List<Page> {
        val cid = response.request.url.queryParameter("cid")
        val cUrl = "$baseUrl/pageapi/viewer/c.php".toHttpUrl().newBuilder()
            .addQueryParameter("cid", cid)
            .build()

        val cRequest = GET(cUrl, headers)
        val cResponse = client.newCall(cRequest).execute()
        val cPhp = try {
            cResponse.parseAs<CPhpResponse>().url
        } catch (_: Exception) {
            throw Exception("This chapter is locked. Log in via WebView and purchase this chapter to read.")
        }

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
