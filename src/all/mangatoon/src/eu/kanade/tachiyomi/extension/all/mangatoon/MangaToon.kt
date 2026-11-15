package eu.kanade.tachiyomi.extension.all.mangatoon

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.md5
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaToon : KeiSource() {
    private val urlLang: String get() = if (lang == "zh") {
        "cn"
    } else if (lang == "pt-BR") {
        "pt"
    } else {
        lang
    }

    // French lists moved to their own host, and the redirect there drops the page number
    private val webUrl: String get() = if (lang == "fr") "https://fr.mangatoon.mobi" else "$baseUrl/$urlLang"

    // Page images and covers come from the *-pic hosts, which have no limit
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(1, 1.seconds) { "-pic" !in it.host }

    override suspend fun getPopularManga(page: Int): MangasPage {
        // Portuguese website doesn't seem to have popular titles.
        val path = if (lang == "pt-BR") "comic" else "hot"
        return mangaListParse(client.get(listUrl(path, page), Fetch.DOCUMENT))
    }

    private fun listUrl(path: String, page: Int): HttpUrl = "$webUrl/genre/$path".toHttpUrl().newBuilder()
        .apply { if (page > 1) addQueryParameter("page", (page - 1).toString()) }
        .build()

    private fun mangaListParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.genre-content div.items a").map { mangaFromElement(it) }
        val hasNextPage = document.selectFirst("span.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangas = mangaListParse(client.get(listUrl("new", page), Fetch.DOCUMENT)).mangas
        // This list has no next link, but every page but the last has a full page of entries
        return MangasPage(mangas, mangas.size >= LIST_PAGE_SIZE)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val searchUrl = "$webUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("word", query)
            .build()
        val response = client.get(searchUrl, Fetch.DOCUMENT, ensureSuccess = false)
        // A search without results is a 404
        if (response.code == 404) {
            response.close()
            return MangasPage(emptyList(), false)
        }
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw HttpException(code)
        }
        val document = response.asJsoup()
        // Some languages link to their own host (fr.mangatoon.mobi, mangatooncom.vn)
        val mangas = document.select("div.comics-result div.recommend-item:has(a[href*=mangatoon])").map { element ->
            SManga.create().apply {
                title = element.select("div.recommend-comics-title").text()
                thumbnail_url = element.select("img").imgAttr().toNormalPosterUrl()
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
            }
        }
        val hasNextPage = document.selectFirst("span.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("div.content-title").text()
        thumbnail_url = element.select("img").imgAttr().toNormalPosterUrl()
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/$urlLang/detail/${manga.contentId}"

    // The website cuts later free episodes short and lists no French episodes; the app API has them all
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val contentId = manga.contentId
        val details = if (fetchDetails) {
            async { api<DetailDto>("/api/content/detail", contentId)!!.toSManga() }
        } else {
            null
        }
        val episodes = if (fetchChapters) {
            async {
                api<List<EpisodeDto>>("/api/content/episodes", contentId)!!
                    .filterNot { it.isFee }
                    .map { it.toSChapter(urlLang, contentId) }
                    .reversed()
            }
        } else {
            null
        }
        SMangaUpdate(details?.await() ?: manga, episodes?.await() ?: chapters)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (contentId, episodeId) = chapter.url.split("/").takeLast(2)
        return "$H5_URL/cartoons/watch/$contentId/$episodeId?_language=$urlLang"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = api<List<PictureDto>>("/api/cartoons/pictures", chapter.url.substringAfterLast("/"))
        .orEmpty()
        .mapNotNull { it.imageUrl }
        .mapIndexed { i, url -> Page(i, imageUrl = url) }

    // The calls of h5.mangatoon.mobi, signed like its script does
    private suspend inline fun <reified T> api(path: String, id: String): T? {
        val query = sortedMapOf(
            "id" to id,
            "_" to Clock.System.now().epochSeconds.toString(),
            "_language" to urlLang,
            "_platform" to "web",
            "_preference" to "girl",
            "_v" to "3.07.00",
            "_webp" to "false",
        ).entries.joinToString("&") { "${it.key}=${it.value}" }
        val sign = (path + query + SIGN_KEY).md5().toHexString()
        return client.get("$API_URL$path?sign=$sign&$query", Fetch.XHR.from("$H5_URL/")).parseAs<DataDto<T>>().data
    }

    private val SManga.contentId: String
        get() = CONTENT_ID_REGEX.find(url)!!.groupValues[1]

    protected open fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    protected open fun Elements.imgAttr(): String = this.first()!!.imgAttr()

    private fun String.toNormalPosterUrl(): String = replace(POSTER_SUFFIX, "$1")

    companion object {
        private const val API_URL = "https://sg.mangatoon.mobi"

        private const val H5_URL = "https://h5.mangatoon.mobi"

        private const val SIGN_KEY = "66c10a61bd916c23f3b33810d3785d17"

        private const val LIST_PAGE_SIZE = 18

        private val POSTER_SUFFIX = "(jpg)-poster(.*)\\d+?$".toRegex()

        // Series links: /en/<slug>?content_id=<id>, /cn/detail/<id>, or /<id>-<slug> on fr.mangatoon.mobi
        private val CONTENT_ID_REGEX = """(?:content_id=|/detail/|^/)(\d+)""".toRegex()
    }
}
