package eu.kanade.tachiyomi.extension.es.thesecretcorps

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class TheSecretCorps : HttpSource() {
    override val name: String = "Ladron Corps"
    override val baseUrl: String = "https://www.ladroncorps.com"
    override val lang: String = "es"
    override val supportsLatest: Boolean = false

    val authorization: String by lazy {
        val response = client.newCall(GET("$baseUrl/_api/v2/dynamicmodel", headers)).execute()
        val json = JSONObject(response.body.string())
        val tokens = json.getJSONObject("apps")
        val keys = tokens.keys()
            .iterator()
            .asSequence()
            .toList()

        tokens.getJSONObject(keys[(0..keys.size).random()])
            .getString("instance")
    }

    private fun apiHeaders(): Headers {
        return headers.newBuilder()
            .set("Authorization", authorization)
            .build()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return listOf(
            SChapter.create().apply {
                name = "Capitulo único"
                setUrlWithoutDomain(document.location())
            },
        )
    }

    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("h1")!!.text()
        description = document.select("div[data-hook='post-description'] p > span")
            .joinToString("\n") { it.text() }

        genre = document.select("#post-footer li a")
            .joinToString { it.text() }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val selectors = "figure[data-hook='imageViewer'] img, img[data-hook='gallery-item-image-img']"
        return document.select(selectors).mapIndexed { index, element ->
            Page(index, document.location(), imageUrl = element.imgAttr())
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    fun Element.imgAttr(): String = when {
        hasAttr("data-pin-media") -> absUrl("data-pin-media")
        else -> absUrl("src")
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val jsonObject = JSONObject(response.body.string())
        val posts = jsonObject.getJSONObject("postFeedPage").getJSONObject("posts")
        val mangaArray = posts.getJSONArray("posts")
        var currentIndex = 0
        val mangas = mutableListOf<SManga>()
        while (currentIndex < mangaArray.length()) {
            val mangaObject = mangaArray.getJSONObject(currentIndex)
            mangas += SManga.create().apply {
                title = mangaObject.getString("title")
                thumbnail_url = mangaObject.getJSONObject("coverMedia")
                    .getJSONObject("image")
                    .getString("url")

                url = mangaObject.getJSONObject("url").getString("path")
            }
            currentIndex++
        }

        return MangasPage(mangas, mangas.isNotEmpty())
    }

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/blog-frontend-adapter-public/v2/post-feed-page".toHttpUrl().newBuilder()
            .addQueryParameter("includeContent", "false")
            .addQueryParameter("languageCode", lang)
            .addQueryParameter("page", "$page")
            .addQueryParameter("pageSize", "20")
            .addQueryParameter("type", "ALL_POSTS")
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val jsonObject = JSONObject(response.body.string())
        val mangaArray = jsonObject.getJSONArray("posts")
        val mangas = mutableListOf<SManga>()
        var currentIndex = 0
        while (currentIndex < mangaArray.length()) {
            val mangaObject = mangaArray.getJSONObject(currentIndex)
            mangas += SManga.create().apply {
                title = mangaObject.getString("title")
                val imagePath = mangaObject.getJSONObject("coverImage")
                    .getJSONObject("src")
                    .getString("file_name")
                thumbnail_url = "$STATIC_MEDIA_URL/$imagePath"
                val slug = mangaObject.getString("seoTitle")
                url = "$baseUrl/post/$slug"
            }
            currentIndex++
        }

        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/_api/communities-blog-node-api/_api/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()
        return GET(url, apiHeaders())
    }

    companion object {
        val STATIC_MEDIA_URL = "https://static.wixstatic.com/media"
        const val URL_SEARCH_PREFIX = "slug:"
    }
}
