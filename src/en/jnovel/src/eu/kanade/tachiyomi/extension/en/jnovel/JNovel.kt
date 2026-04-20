package eu.kanade.tachiyomi.extension.en.jnovel

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class JNovel : HttpSource() {
    override val name = "J-Novel"
    override val baseUrl = "https://j-novel.club"
    override val lang = "en"
    override val supportsLatest = false

    private val viewerUrl = "https://labs.j-novel.club/embed/v2"
    private val decoder = Decoder()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("type", "manga")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.TitleListEntry-module__CVV_2G__entry").mapNotNull {
            val link = it.selectFirst("a[href^=/series/]") ?: return@mapNotNull null
            SManga.create().apply {
                title = it.selectFirst("h2")!!.text()
                setUrlWithoutDomain(link.absUrl("href"))
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
            }
        }

        val nextButton = document.selectFirst("div.button:has(div.text:contains(Next))")
        val hasNextPage = nextButton != null && !nextButton.classNames().contains("disabled")
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("type", "manga")

        if (query.isNotEmpty()) {
            url.addQueryParameter("search", query)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("meta[property='og:title']")!!.attr("content")
            description = document.selectFirst("meta[name='description']")?.attr("content")
            thumbnail_url = document.selectFirst("meta[property='og:image']")?.absUrl("content")

            val authors = document.select("meta[property='book:author']")
                .mapNotNull {
                    it.attr("content")
                        .substringAfter("search=")
                        .substringAfter(":")
                        .replace("\"", "")
                        .trim()
                        .takeIf { name -> name.isNotEmpty() }
                }
            author = authors.distinct().joinToString()
            genre = document.select("meta[property='book:tag']").joinToString { it.attr("content") }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select("a[class*=TitleVolumeProgress-module__][class*=__part][href^=/read/]").mapNotNull {
            val href = it.absUrl("href")
            if (href.isBlank()) return@mapNotNull null

            val chapterName = it.text()
                .replace(Regex("\\s+"), " ")
                .trim()

            SChapter.create().apply {
                setUrlWithoutDomain(href)
                name = chapterName
            }
        }
            .distinctBy { it.url }
            .reversed()
        return chapters
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val embedUrl = document.selectFirst("iframe[src^='$viewerUrl']")!!.absUrl("src")
        val embedDocument = client.newCall(GET(embedUrl, headers)).execute().asJsoup()
        val manifestUrlStr = embedDocument.body().absUrl("data-e4p-manifest")
        val manifestUrl = manifestUrlStr.toHttpUrl()
        val manifestResponse = client.newCall(GET(manifestUrlStr, headers)).execute()
        val ticketBytes = manifestResponse.use { it.body.bytes() }
        val pub = decoder.decodeManifest(ticketBytes)
        val manifestQueryNames = manifestUrl.queryParameterNames

        return pub.spine.mapIndexedNotNull { index, link ->
            val h2048 = link.variants.firstOrNull {
                it.link.contains("h2048") && it.image != null
            } ?: return@mapIndexedNotNull null

            val resolved = manifestUrl.resolve(h2048.link) ?: return@mapIndexedNotNull null
            val withAuth = resolved.newBuilder().apply {
                manifestQueryNames.forEach { name ->
                    manifestUrl.queryParameter(name)?.let { setQueryParameter(name, it) }
                }
            }.build()

            Page(index, imageUrl = withAuth.toString())
        }
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
