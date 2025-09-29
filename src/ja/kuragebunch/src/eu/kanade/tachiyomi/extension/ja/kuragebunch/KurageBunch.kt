package eu.kanade.tachiyomi.extension.ja.kuragebunch

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class KurageBunch : GigaViewer(
    "Kurage Bunch",
    "https://kuragebunch.com",
    "ja",
    "https://cdn-img.kuragebunch.com",
    isPaginated = true,
) {

    override val supportsLatest: Boolean = false

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override val publisher: String = "株式会社"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series/kuragebunch", headers)

    override fun popularMangaSelector(): String = "ul.page-series-list li div.item-box"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("a.series-data-container h4")!!.text()
        thumbnail_url = element.selectFirst("a.series-thumb img")!!.attr("data-src")
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override fun chapterListSelector(): String = "li.episode"

    override fun pageListParse(document: Document): List<Page> {
        val episodeJson = document.selectFirst("script#episode-json")?.attr("data-value")
            ?: return emptyList()

        val episode = try {
            episodeJson.parseAs<PageListDto>()
        } catch (e: SerializationException) {
            throw Exception("このチャプターは非公開です\nChapter is not available!")
        }
        val isScrambled = episode.readableProduct.pageStructure.choJuGiga == "baku"

        return episode.readableProduct.pageStructure.pages
            .filter { it.type == "main" }
            .mapIndexed { i, page ->
                val imageUrl = page.src.toHttpUrl().newBuilder().apply {
                    if (isScrambled) {
                        addQueryParameter("width", page.width.toString())
                        addQueryParameter("height", page.height.toString())
                        addQueryParameter("descramble", "true")
                    }
                }.toString()
                Page(i, document.location(), imageUrl)
            }
    }

    override fun imageIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isScrambled = request.url.queryParameter("descramble") == "true"

        if (!isScrambled) {
            return chain.proceed(request)
        }
        return super.imageIntercept(chain)
    }

    override fun getCollections(): List<Collection> = listOf(
        Collection("くらげバンチ", "kuragebunch"),
        Collection("読切", "oneshot"),
        Collection("月刊コミックバンチ", "comicbunch"),
        Collection("Bバンチ", "bbunch"),
        Collection("ututu", "ututu"),
    )
}
