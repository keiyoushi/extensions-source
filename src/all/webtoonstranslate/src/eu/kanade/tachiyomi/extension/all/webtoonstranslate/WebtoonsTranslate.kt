package eu.kanade.tachiyomi.extension.all.webtoonstranslate

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable

class WebtoonsTranslate(
    override val lang: String,
    private val translateLangCode: String,
    private val extensionId: Long? = null,
) : HttpSource() {

    override val name = "Webtoons.com Translations"

    override val baseUrl = "https://translate.webtoons.com"

    override val id get() = extensionId ?: super.id

    override val supportsLatest = false

    override val client = network.cloudflareClient

    private val apiBaseUrl = "https://global.apis.naver.com"
    private val mobileBaseUrl = "https://m.webtoons.com"

    private val pageSize = 24

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", mobileBaseUrl)

    private fun mangaRequest(page: Int, requestSize: Int): Request {
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegments("lineWebtoon/ctrans/translatedWebtoons_jsonp.json")
            .addQueryParameter("orderType", "UPDATE")
            .addQueryParameter("offset", "${(page - 1) * requestSize}")
            .addQueryParameter("size", "$requestSize")
            .addQueryParameter("languageCode", translateLangCode)
            .build()
        return GET(url, headers)
    }

    // Webtoons translations doesn't really have a "popular" sort; just "UPDATE", "TITLE_ASC",
    // and "TITLE_DESC".  Pick UPDATE as the most useful sort.
    override fun popularMangaRequest(page: Int): Request = mangaRequest(page, pageSize)

    override fun popularMangaParse(response: Response): MangasPage {
        val offset = response.request.url.queryParameter("offset")!!.toInt()
        val result = response.parseAs<Result<TitleList>>()

        assert(result.code == "000") {
            "Error getting popular manga: error code ${result.code}"
        }

        val mangaList = result.result!!.titleList
            .map { it.toSManga(mobileBaseUrl, translateLangCode) }

        return MangasPage(mangaList, hasNextPage = result.result.totalCount > pageSize + offset)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = client.newCall(searchMangaRequest(page, query, filters))
        .asObservableSuccess()
        .map { response ->
            searchMangaParse(response, query)
        }

    /**
     * Don't see a search function for Fan Translations, so let's do it client side.
     * There's 75 webtoons as of 2019/11/21, a hardcoded request of 200 should be a sufficient request
     * to get all titles, in 1 request, for quite a while
     */
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = mangaRequest(page, 200)

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val mangas = popularMangaParse(response).mangas
            .filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.author?.contains(query, ignoreCase = true) == true ||
                    it.artist?.contains(query, ignoreCase = true) == true
            }

        return MangasPage(mangas, hasNextPage = false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        val (webtoonAuthor, webtoonArtist) = document.getMetaProp("com-linewebtoon:webtoon:author").let {
            val split = it.split(" / ", limit = 2)
            if (split.count() > 1) {
                split[0] to split[1]
            } else {
                it to it
            }
        }

        return SManga.create().apply {
            title = document.getMetaProp("og:title")
            artist = webtoonAuthor
            author = webtoonArtist
            description = document.getMetaProp("og:description")
            thumbnail_url = document.getMetaProp("og:image")
        }
    }

    private fun Document.getMetaProp(property: String): String = head().select("meta[property=\"$property\"]").attr("content")

    override fun chapterListRequest(manga: SManga): Request {
        val (titleNo, teamVersion) = manga.url.toHttpUrl().let {
            it.queryParameter("titleNo") to it.queryParameter("teamVersion")
        }
        val chapterListUrl = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegments("lineWebtoon/ctrans/translatedEpisodes_jsonp.json")
            .addQueryParameter("titleNo", titleNo)
            .addQueryParameter("languageCode", translateLangCode)
            .addQueryParameter("offset", "0")
            .addQueryParameter("limit", "10000")
            .addQueryParameter("teamVersion", teamVersion)
            .build()

        return GET(chapterListUrl, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<Result<EpisodeList>>()

        assert(result.code == "000") {
            val message = result.message ?: "error ${result.code}"
            throw Exception("Error getting chapter list: $message")
        }

        return result.result!!.episodes
            .filter { it.translateCompleted }
            .map { it.toSChapter() }
            .reversed()
    }

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiBaseUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<Result<ImageList>>()

        return result.result!!.imageInfo.mapIndexed { i, img ->
            Page(i, imageUrl = img.imageUrl)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
