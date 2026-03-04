package eu.kanade.tachiyomi.extension.ja.pashup

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
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

class PashUp : HttpSource() {
    override val name = "Pash Up!"
    override val baseUrl = "https://pash-up.jp"
    override val lang = "ja"
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/pageapi"
    private val pageLimit = 10
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(PublusInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/contents.php".toHttpUrl().newBuilder()
            .addQueryParameter("type", "ranking")
            .addQueryParameter("period", "daily")
            .addQueryParameter("category", "2")
            .addQueryParameter("limit", pageLimit.toString())
            .addQueryParameter("offset", ((page - 1) * pageLimit).toString())
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val offset = response.request.url.queryParameter("offset")!!.toInt()
        val limit = response.request.url.queryParameter("limit")!!.toInt()
        val result = response.parseAs<EntryResponse>()
        val mangas = result.contents.map { it.toSManga() }
        val hasNextPage = result.totalResults > (offset + limit)
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/products.php".toHttpUrl().newBuilder()
            .addQueryParameter("type", "update")
            .addQueryParameter("period", "daily")
            .addQueryParameter("category", "2")
            .addQueryParameter("unit", "2")
            .addQueryParameter("lastest", "1")
            .addQueryParameter("limit", pageLimit.toString())
            .addQueryParameter("offset", ((page - 1) * pageLimit).toString())
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/contents.php".toHttpUrl().newBuilder()
            .addQueryParameter("type", "search")
            .addQueryParameter("reserve", "1")
            .addQueryParameter("keyword", query)
            .addQueryParameter("limit", "9999")
            .addQueryParameter("_", System.currentTimeMillis().toString())
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<EntryResponse>()
        val mangas = result.contents.filter { it.category == "2" }.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$apiUrl/products.php".toHttpUrl().newBuilder()
            .addQueryParameter("type", "contents")
            .addQueryParameter("limit", "1")
            .addQueryParameter("id", manga.url)
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<EntryResponse>().contents.first().toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/content/${manga.url}"

    override fun chapterListRequest(manga: SManga): Request {
        val url = "$apiUrl/products.php".toHttpUrl().newBuilder()
            .addQueryParameter("type", "contents")
            .addQueryParameter("id", manga.url)
            .addQueryParameter("unit", "2")
            .addQueryParameter("limit", "9999")
            .addQueryParameter("order", "nodesc")
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ChapterResponse>()
        val now = System.currentTimeMillis()
        val allProducts = mutableListOf<Pair<String, Product>>()

        result.contents.forEach { content ->
            allProducts.add(content.seriesId to content.product)
            content.productMinMax?.values?.forEach { minMax ->
                minMax.min?.let { allProducts.add(content.seriesId to it) }
                minMax.max?.let { allProducts.add(content.seriesId to it) }
            }
        }

        val uniqueProducts = allProducts
            .distinctBy { it.second.id }
            .filter { (_, product) ->
                val endTime = dateFormat.tryParse(product.endDate)
                endTime == 0L || endTime > now
            }

        val grouped = uniqueProducts.groupBy { it.second.salesUnit }
        val chapters = (grouped["1"] ?: emptyList()).sortedByDescending { dateFormat.tryParse(it.second.startDate) }
        val volumes = (grouped["2"] ?: emptyList()).sortedByDescending { dateFormat.tryParse(it.second.startDate) }

        return (chapters + volumes).map { it.second.toSChapter(dateFormat, it.first) }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterUrl = "$baseUrl/${chapter.url}".toHttpUrl()
        val productId = chapterUrl.fragment
        val seriesId = chapterUrl.pathSegments.first()

        val url = "$apiUrl/products.php".toHttpUrl().newBuilder()
            .addQueryParameter("type", "contents")
            .addQueryParameter("id", seriesId)
            .addQueryParameter("unit", "2")
            .addQueryParameter("limit", "9999")
            .addQueryParameter("order", "nodesc")
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .build()

        val response = client.newCall(GET(url, headers)).execute()
        val result = response.parseAs<ChapterResponse> { it }

        val allProducts = mutableListOf<Product>()
        result.contents.forEach { content ->
            allProducts.add(content.product)
            content.productMinMax?.values?.forEach { minMax ->
                minMax.min?.let { allProducts.add(it) }
                minMax.max?.let { allProducts.add(it) }
            }
        }

        return allProducts
            .distinctBy { it.id }
            .find { it.id == productId }!!
            .downloadUrl
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = "$baseUrl/${chapter.url}".toHttpUrl()
        val productId = chapterUrl.fragment
        val seriesId = chapterUrl.pathSegments.first()
        val url = "$apiUrl/products.php".toHttpUrl().newBuilder()
            .addQueryParameter("type", "contents")
            .addQueryParameter("id", seriesId)
            .addQueryParameter("unit", "2")
            .addQueryParameter("limit", "9999")
            .addQueryParameter("order", "nodesc")
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .fragment(productId)
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val productId = response.request.url.fragment
        val chapterResult = response.parseAs<ChapterResponse>()
        val allProducts = mutableListOf<Product>()
        chapterResult.contents.forEach { content ->
            allProducts.add(content.product)
            content.productMinMax?.values?.forEach { minMax ->
                minMax.min?.let { allProducts.add(it) }
                minMax.max?.let { allProducts.add(it) }
            }
        }

        val product = allProducts.distinctBy { it.id }.find { it.id == productId }
        val cid = product!!.downloadUrl.toHttpUrl().queryParameter("cid")

        val cUrl = "$baseUrl/pageapi/viewer/c.php".toHttpUrl().newBuilder()
            .addQueryParameter("cid", cid)
            .build()

        val cRequest = GET(cUrl, headers)
        val cResponse = client.newCall(cRequest).execute()
        val cPhp = try {
            cResponse.parseAs<CPhpResponse>().url
        } catch (_: Exception) {
            throw Exception("Log in via WebView and purchase this chapter to read.")
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

    override fun imageUrlParse(response: Response): String = response.request.url.toString()
}
