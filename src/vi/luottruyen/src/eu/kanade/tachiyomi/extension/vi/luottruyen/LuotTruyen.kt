package eu.kanade.tachiyomi.extension.vi.luottruyen

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.textOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document

@Source
abstract class LuotTruyen : WPComics() {
    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
    }

    override val searchPath = "tim-truyen-nang-cao"

    override fun getSortList() = netTruyenSortList()

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaPage(
        client.get("$baseUrl/tim-truyen?status=-1&sort=10&page=$page", Fetch.DOCUMENT),
        popularMangaSelector(),
        ::popularMangaFromElement,
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaPage(
        client.get("$baseUrl/$searchPath?status=-1&sort=0&advancedSearch=true&page=$page", Fetch.DOCUMENT),
        latestUpdatesSelector(),
        ::latestUpdatesFromElement,
    )

    override fun popularMangaNextPageSelector() = "li.next:not(.disabled) a"

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/$searchPath".toHttpUrl().newBuilder().apply {
            filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()?.let { genre ->
                addQueryParameter("categories", genre)
                addQueryParameter("includeCategories", "true")
            }
            if (query.isNotBlank()) addQueryParameter("keyword", query)
            filters.firstInstanceOrNull<SortFilter>()?.toUriPart()?.let { addQueryParameter("sort", it) }
            filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()?.let { addQueryParameter("status", it) }
            addQueryParameter("advancedSearch", "true")
            addQueryParameter("page", page.toString())
        }.build()

        return parseMangaPage(client.get(url, Fetch.DOCUMENT), searchMangaSelector(), ::searchMangaFromElement)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "truyen-tranh") return null

        val mangaSlug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val mangaPath = when {
            url.pathSegments.size == 2 && mangaSlug.hasNumericIdSuffix() -> url.encodedPath
            url.pathSegments.size == 4 &&
                url.pathSegments[2].startsWith("chapter-") &&
                url.pathSegments[3].isNotEmpty() -> {
                client.get(url, Fetch.DOCUMENT).asLoggedInJsoup()
                    .select(".breadcrumb a[href]")
                    .mapNotNull { it.absUrl("href").toHttpUrlOrNull() }
                    .firstOrNull {
                        it.pathSegments.size == 2 &&
                            it.pathSegments[0] == "truyen-tranh" &&
                            it.pathSegments[1].startsWith("$mangaSlug-") &&
                            it.pathSegments[1].hasNumericIdSuffix()
                    }
                    ?.encodedPath
            }
            else -> null
        } ?: return null

        val manga = SManga.create().apply { setUrlWithoutDomain(mangaPath) }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        document.selectFirst("article#item-detail")?.let { infoElement ->
            author = infoElement.selectFirst("li.author p.col-xs-8, li.author p.col-xs-10")?.text()
            status = infoElement.selectFirst("li.status p.col-xs-8, li.status p.col-xs-10")?.text().toStatus()
            genre = infoElement.select("li.kind p.col-xs-8 a, li.kind p.col-xs-12 a").joinToString { it.text() }.ifEmpty { null }
        }
    }

    override suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val headers = headersBuilder().set("X-Requested-With", "XMLHttpRequest").build()
        val body = FormBody.Builder().add("StoryID", manga.url.substringAfterLast("-")).build()
        return client.post("$baseUrl/Story/ListChapterByStoryID", headers, Fetch.XHR.from(getMangaUrl(manga)), body).asJsoup()
            .select("li.row:not(.heading)")
            .map(::chapterFromElement)
    }

    override suspend fun parsePageList(response: Response): List<Page> {
        val document = response.asLoggedInJsoup()
        val images = document.select("#view-chapter img")
            .ifEmpty {
                document.select(".chapter-content img, .reading-content img, .content-chapter img, .reading-detail .page-chapter img[data-index]")
            }

        return images.mapIndexed { index, image -> Page(index, imageUrl = image.absUrl("src")) }
    }

    override fun parseGenres(document: Document): List<Pair<String?, String>> = listOf(Pair(null, intl["STATUS_ALL"])) +
        document.select("input[name=categories]").mapNotNull { input ->
            val name = input.parent()?.selectFirst("label[for=${input.id()}]")?.textOrNull() ?: return@mapNotNull null
            val value = input.attrOrNull("value") ?: return@mapNotNull null
            Pair(value, name)
        }.distinctBy { it.first }

    private fun Response.asLoggedInJsoup(): Document {
        if (request.url.encodedPath == "/Account/Login") {
            close()
            throw Exception("Đăng nhập bằng Google trong WebView để đọc truyện.")
        }
        return asJsoup()
    }

    private fun String.hasNumericIdSuffix(): Boolean = substringAfterLast('-', missingDelimiterValue = "").let { it.isNotEmpty() && it.all(Char::isDigit) }
}
