package eu.kanade.tachiyomi.extension.pt.instahentai

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class Instahentai : KeiSource() {

    private val host get() = baseUrl.toHttpUrl().host

    // Covers on /wp-content/ load with every listing page
    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == host && !it.encodedPath.startsWith("/wp-content/") }

    override suspend fun getPopularManga(page: Int): MangasPage = getListing("melhores", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getListing("novos-hentai", page)

    private suspend fun getListing(path: String, page: Int): MangasPage = client.get(listingUrl(path, page), Fetch.DOCUMENT).asJsoup().toMangasPage()

    private fun listingUrl(path: String, page: Int): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(path)
        .apply { if (page > 1) addPathSegments("page/$page") }
        .addPathSegment("")
        .build()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) addPathSegments("page/$page").addPathSegment("")
            addQueryParameter("s", query)
            filters.firstInstanceOrNull<CategoryFilter>()?.selected?.let { addQueryParameter("category_name", it) }
            filters.firstInstanceOrNull<GenreFilter>()?.state?.filter { it.state }?.takeIf { it.isNotEmpty() }
                // WordPress reads "+" between terms as AND
                ?.let { genres -> addQueryParameter("generos", genres.joinToString("+") { it.slug }) }
            filters.firstInstanceOrNull<StatusFilter>()?.selected?.let { addQueryParameter("status", it) }
            filters.firstInstanceOrNull<ColorFilter>()?.selected?.let { addQueryParameter("cor", it) }
        }.build()
        return client.get(url, Fetch.DOCUMENT).asJsoup().toMangasPage()
    }

    private fun Document.toMangasPage(): MangasPage {
        val mangas = select("article.card_item:has(a[href*=/serie/])").map { it.toSManga() }
        return MangasPage(mangas, hasNextPage())
    }

    // Both pagers end with the next link; the site's own one keeps it, pointing to #, on the last page
    private fun Document.hasNextPage() = selectFirst(".pagination > a:last-child:not([href='#'])") != null

    private fun Element.toSManga() = SManga.create().apply {
        val link = selectFirst(".serie_title a")!!
        url = link.slug()
        title = link.text()
        thumbnail_url = selectFirst(".picture img")?.imageUrl()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != host) return null
        val pathSlug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val slug = when (url.pathSegments[0]) {
            "serie" -> pathSlug
            "ler" -> client.get("$baseUrl/ler/$pathSlug/", Fetch.DOCUMENT).asJsoup()
                .selectFirst(".chapter_header .poster a[href*=/serie/]")?.slug()
                ?: return null
            else -> return null
        }
        return client.get("$baseUrl/serie/$slug/", Fetch.DOCUMENT).asJsoup().toSManga(slug).apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/serie/${manga.url}/"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/ler/${chapter.url}/"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val details = document.toSManga(manga.url)
        // The series is modified when a chapter is added
        val updated = document.selectFirst("script.yoast-schema-graph")?.data()?.parseAs<SchemaGraphDto>()
            ?.graph?.firstOrNull { it.type == "WebPage" }?.dateModified
        val chapterList = document.select("#section_cap a").reversed().mapIndexed { index, link ->
            SChapter.create().apply {
                url = link.slug()
                name = link.text().removePrefix("${details.title} - ")
                if (index == 0) date_upload = Instant.tryParse(updated)
            }
        }
        return SMangaUpdate(details, chapterList)
    }

    private fun Document.toSManga(slug: String) = SManga.create().apply {
        url = slug
        val info = selectFirst(".data_main")!!
        title = info.selectFirst("h1.titulo_serie")!!.text()
        author = info.select("a[href*=/autor/]").joinToString { it.text() }.ifEmpty { null }
        artist = info.select("a[href*=/arte/]").joinToString { it.text() }.ifEmpty { null }
        genre = info.select("a[href*=/categoria/], a[href*=/generos/]").joinToString { it.text() }.ifEmpty { null }
        description = info.selectFirst("#textDiv")?.textOrNull()
        thumbnail_url = selectFirst(".sheader_content .poster img")?.imageUrl()
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select("section.homeModule article.card_item:has(a[href*=/serie/])")
        .map { it.toSManga() }

    // Last token the reader accepted; the site rotates it every few minutes and keeps old ones valid for a while
    @Volatile
    private var readerToken: String? = null

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        var document = getReaderPage(chapter, readerToken)
        if (document.selectFirst("#Imagens") == null) {
            // Without a valid token the reader shows an ad link that returns to the chapter with the current one
            readerToken = document.selectFirst("a[href*='?a='][href*=token]")?.absUrl("href")?.toHttpUrl()?.queryParameter("a")
                ?.let { baseUrl.toHttpUrl().resolve(it)?.queryParameter("token") }
                ?: throw Exception("Reader token not found")
            document = getReaderPage(chapter, readerToken)
        }
        return document.select("#Imagens img[data-src]").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("data-src"))
        }
    }

    private suspend fun getReaderPage(chapter: SChapter, token: String?): Document {
        val url = getChapterUrl(chapter).toHttpUrl().newBuilder()
            .apply { if (token != null) addQueryParameter("token", token) }
            .build()
        return client.get(url, Fetch.DOCUMENT).asJsoup()
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val genres = mutableListOf<GenreDto>()
        var page = 1
        do {
            val document = client.get(listingUrl("hentai-generos", page++), Fetch.DOCUMENT).asJsoup()
            document.select("article.card_item .serie_title a[href*=/generos/]")
                .mapTo(genres) { GenreDto(it.text(), it.slug()) }
        } while (document.hasNextPage())
        return genres.sortedBy { it.name.lowercase() }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreDto>>()
        return FilterList(
            listOfNotNull(
                CategoryFilter(),
                StatusFilter(),
                ColorFilter(),
                genres?.let(::GenreFilter),
            ),
        )
    }

    private fun Element.imageUrl(): String? = absUrl("data-src").ifEmpty { absUrl("src") }.ifEmpty { null }

    private fun Element.slug() = absUrl("href").toHttpUrl().pathSegments[1]
}
