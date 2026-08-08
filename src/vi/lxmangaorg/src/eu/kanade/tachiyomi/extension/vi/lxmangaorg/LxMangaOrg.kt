package eu.kanade.tachiyomi.extension.vi.lxmangaorg

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class LxMangaOrg : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(5)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaPage(
        client.get(pagedUrl("/truyen-tranh-hot", page)),
        page,
    )

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaPage(
        client.get(pagedUrl("/moi-cap-nhat", page)),
        page,
    )

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
                .build()
        } else {
            val path = filters.firstInstanceOrNull<ClassificationFilter>()?.selectedPath()
                ?: filters.firstInstanceOrNull<GenreFilter>()?.selectedPath()
                ?: filters.firstInstanceOrNull<DoujinshiFilter>()?.selectedPath()
                ?: filters.firstInstanceOrNull<AuthorFilter>()?.selectedPath()
                ?: "/moi-cap-nhat"
            "$baseUrl$path".toHttpUrl()
        }

        return parseMangaPage(client.get(pagedUrl(url, page)), page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val mangaPath = when {
            url.pathSegments.size == 1 && url.pathSegments[0].endsWith(".html") -> url.encodedPath
            url.pathSegments.size == 2 && url.pathSegments[1].endsWith(".html") -> "/${url.pathSegments[0]}.html"
            else -> return null
        }

        val manga = SManga.create().apply {
            setUrlWithoutDomain(mangaPath)
        }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    private fun parseMangaPage(response: Response, page: Int): MangasPage {
        val document = response.asJsoup()
        val mangaList = document.select("a.comic-link[href$=.html]")
            .distinctBy { it.absUrl("href") }
            .map(::mangaFromElement)
        val hasNextPage = document.select("a.page-link[data-page=${page + 1}]").isNotEmpty()

        return MangasPage(mangaList, hasNextPage)
    }

    private fun mangaFromElement(titleElement: Element): SManga {
        val card = titleElement.closest("div.card")
        val thumbnail = card?.selectFirst("a.comic-tmb img.card-img-top")

        return SManga.create().apply {
            title = titleElement.text()
            setUrlWithoutDomain(titleElement.absUrl("href"))
            thumbnail_url = thumbnail?.absUrl("data-src")
                ?.ifEmpty { thumbnail.absUrl("src") }
                ?.ifEmpty { null }
        }
    }

    // ============================== Details ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        return SMangaUpdate(
            manga = if (fetchDetails) parseMangaDetails(document, manga) else manga,
            chapters = if (fetchChapters) fetchChapterList(document, manga.url) else chapters,
        )
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        val metadata = listOf("Danh mục", "Thể loại", "Quốc gia")
            .flatMap { label -> document.detailLinks(label) }
            .map { it.text() }
            .distinct()
        val statusText = document.select("h5").text().lowercase()

        setUrlWithoutDomain(manga.url)
        title = document.selectFirst("h1.comic-title")!!.text()
        author = document.detailLinks("Tác giả").joinToString { it.text() }.ifBlank { null }
        genre = metadata.joinToString().ifBlank { null }
        thumbnail_url = document.selectFirst("img.img-thumbnail[alt]")?.absUrl("src")
        status = when {
            "đã hoàn thành" in statusText -> SManga.COMPLETED
            "đang tiến hành" in statusText -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    private fun Document.detailLinks(label: String): List<Element> = select("div.comic-details__item")
        .firstOrNull { item ->
            item.children().firstOrNull { it.hasClass("comic-details__label") }?.text() == label
        }
        ?.children()
        ?.firstOrNull { it.hasClass("comic-details__item_links") }
        ?.select("a")
        .orEmpty()

    private suspend fun fetchChapterList(document: Document, mangaUrl: String): List<SChapter> {
        val mangaId = document.selectFirst("meta[name=post-id]")?.attr("content")
            ?: document.selectFirst("script:containsData(post_id)")?.data()
                ?.let { postIdRegex.find(it)?.groupValues?.get(1) }
            ?: return emptyList()
        val nonce = document.selectFirst("#chapters_list_nonce")?.attr("value")
            ?: return emptyList()
        val body = FormBody.Builder()
            .add("action", "baka_ajax")
            .add("type", "get_chapters_list")
            .add("id", mangaId)
            .add("chap", "0")
            .add("chapters_list_nonce", nonce)
            .build()
        val ajaxHeaders = headersBuilder()
            .set("Referer", "$baseUrl$mangaUrl")
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        return client.post("$baseUrl/wp-admin/admin-ajax.php", ajaxHeaders, body)
            .parseAs<ChapterListResponse>()
            .data
            .chapters
            .map { chapter ->
                SChapter.create().apply {
                    name = chapter.title
                    setUrlWithoutDomain(chapter.link)
                }
            }
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client
        .get("$baseUrl${chapter.url}")
        .asJsoup()
        .select("img[alt^=Trang truyện]")
        .mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }

    // ============================== Filters ===============================

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val classifications = async { fetchClassifications() }
        val genres = async { fetchSitemapOptions("/genre-sitemap.xml", "/genre/") }
        val doujinshi = async { fetchSitemapOptions("/doujinshi-sitemap.xml", "/doujinshi/") }
        val authors = async { fetchDirectoryPage("/tac-gia", "/artist/") }

        FilterData(
            classifications = classifications.await(),
            genres = genres.await(),
            doujinshi = doujinshi.await(),
            authors = authors.await(),
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>() ?: return FilterList()
        return getFilters(filterData)
    }

    private suspend fun fetchClassifications(): List<FilterOption> = client.get(baseUrl).asJsoup()
        .select("a[href]")
        .mapNotNull { element ->
            val url = element.absUrl("href").toHttpUrlOrNull() ?: return@mapNotNull null
            val path = buildString {
                append(url.encodedPath)
                url.encodedQuery?.let { append('?', it) }
            }
            val isClassification = path == "/moi-cap-nhat" ||
                path == "/truyen-tranh-hot" ||
                path == "/da-hoan-thanh" ||
                path.startsWith("/moi-cap-nhat?sort=") ||
                path.startsWith("/category/")

            if (isClassification && element.text().isNotEmpty()) {
                FilterOption(element.text(), path)
            } else {
                null
            }
        }
        .distinctBy { it.path }

    private suspend fun fetchSitemapOptions(sitemapPath: String, optionPath: String): List<FilterOption> = client
        .get("$baseUrl$sitemapPath")
        .asJsoup()
        .select("loc")
        .mapNotNull { element ->
            val url = element.text().toHttpUrlOrNull() ?: return@mapNotNull null
            if (!url.encodedPath.startsWith(optionPath)) return@mapNotNull null

            val name = url.pathSegments.last()
                .replace('-', ' ')
                .replaceFirstChar { it.titlecase() }
            FilterOption(name, url.encodedPath)
        }
        .distinctBy { it.path }

    private suspend fun fetchDirectoryPage(directoryPath: String, optionPath: String): List<FilterOption> = parseDirectoryOptions(client.get("$baseUrl$directoryPath").asJsoup(), optionPath)

    private fun parseDirectoryOptions(document: Document, optionPath: String): List<FilterOption> = document
        .select("div.channel-item__name_details a[href*=$optionPath]")
        .map { element -> FilterOption(element.text(), element.absUrl("href").toHttpUrl().encodedPath) }
        .filter { it.name.isNotEmpty() }
        .distinctBy { it.path }

    // ============================= Utilities ==============================

    private fun pagedUrl(path: String, page: Int): HttpUrl = pagedUrl("$baseUrl$path".toHttpUrl(), page)

    private fun pagedUrl(url: HttpUrl, page: Int): HttpUrl {
        if (page <= 1) return url

        return url.newBuilder()
            .encodedPath("${url.encodedPath.trimEnd('/')}/page/$page")
            .build()
    }

    private val postIdRegex = Regex("""post_id["']?\s*:\s*["'](\d+)["']""")
}
