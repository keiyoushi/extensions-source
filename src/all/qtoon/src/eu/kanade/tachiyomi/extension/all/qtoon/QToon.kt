package eu.kanade.tachiyomi.extension.all.qtoon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.toJsonString
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class QToon(
    override val lang: String,
    private val siteLang: String,
) : HttpSource() {
    override val name = "QToon"

    private val domain = "qtoon.com"
    override val baseUrl = "https://$domain"
    private val apiUrl = "https://api.$domain"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("api/w/album/page/comics")
            addQueryParameter("page", page.toString())
            addQueryParameter("asid", "as_l9zC15glGlkcS7yIamHQ")
        }.build()

        return apiRequest(url)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.decryptAs<Comics>()

        return MangasPage(
            mangas = data.comics.map { comic ->
                SManga.create().apply {
                    url = ComicUrl(comic.csid, comic.webLinkId).toJsonString()
                    title = comic.title
                    thumbnail_url = comic.image.thumb.url
                }
            },
            hasNextPage = data.more == 1,
        )
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("api/w/ranking/page/comics")
            addQueryParameter("page", page.toString())
            addQueryParameter("rsid", "daily_hot")
        }.build()

        return apiRequest(url)
    }

    override fun latestUpdatesParse(response: Response) =
        popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        throw Exception("Not implemented")
    }

    override fun searchMangaParse(response: Response): MangasPage {
        throw Exception("Not implemented")
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        throw Exception("Not implemented")
    }

    override fun mangaDetailsParse(response: Response): SManga {
        throw Exception("Not implemented")
    }

    override fun chapterListRequest(manga: SManga): Request {
        throw Exception("Not implemented")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        throw Exception("Not implemented")
    }

    override fun pageListRequest(chapter: SChapter): Request {
        throw Exception("Not implemented")
    }

    override fun pageListParse(response: Response): List<Page> {
        throw Exception("Not implemented")
    }

    override fun imageUrlParse(response: Response): String {
        throw Exception("Not implemented")
    }

    private fun apiRequest(url: HttpUrl): Request {
        val headers = headersBuilder().apply {
            val platform = mobileUserAgentRegex.containsMatchIn(headers["User-Agent"]!!)
            add("platform", if (platform) "h5" else "pc")
            add("lth", siteLang)
            add("did", randomToken)
        }.build()

        return GET(url, headers)
    }
}
