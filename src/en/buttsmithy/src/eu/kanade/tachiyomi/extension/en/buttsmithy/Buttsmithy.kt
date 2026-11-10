package eu.kanade.tachiyomi.extension.en.buttsmithy

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class Buttsmithy : KeiSource() {

    // Alfie lives on its own host; the other comics are on incase.buttsmithy.com
    private val baseUrlAlfie = "https://buttsmithy.com"

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(TextInterceptor())
        .rateLimit(3) { it.encodedPath.startsWith("/archives/chapter/") }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(fetchAllComics(), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val comics = fetchAllComics().filter { it.title.contains(query, ignoreCase = true) }
        return MangasPage(comics, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = when (url.host) {
        baseUrlAlfie.toHttpUrl().host -> alfieManga()
        baseUrl.toHttpUrl().host -> {
            val document = client.get(url, Fetch.DOCUMENT).asJsoup()
            val firstPage = document.firstPageUrl()?.toHttpUrl()?.encodedPath
            fetchAllComics().firstOrNull { it.url == url.encodedPath || it.url == firstPage }
        }
        else -> null
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = when {
        manga.url.startsWith(baseUrlAlfie) -> SMangaUpdate(alfieManga(), if (fetchChapters) fetchAlfieChapters() else chapters)
        fetchChapters -> SMangaUpdate(manga, fetchComicPagesAsChapters(getMangaUrl(manga)).asReversed())
        else -> SMangaUpdate(manga, chapters)
    }

    // Alfie manga and all chapters store absolute urls
    override fun getMangaUrl(manga: SManga): String = if (manga.url.startsWith(baseUrlAlfie)) manga.url else baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = chapter.url

    private suspend fun fetchAlfieChapters(): List<SChapter> {
        val document = client.get(baseUrlAlfie, Fetch.DOCUMENT).asJsoup()
        return document.select("#chapter option.level-0")
            .map { option ->
                SChapter.create().apply {
                    url = option.absUrl("value")
                    name = option.text()
                }
            }
            // the site lists its chapters out of order; Misc (holiday extras) goes on top
            .sortedByDescending { CHAPTER_NUMBER_REGEX.find(it.name)?.value?.toFloat() ?: Float.MAX_VALUE }
    }

    // The other comics have no page list, so each page is a chapter, found by following "Next"
    private suspend fun fetchComicPagesAsChapters(comicUrl: String): List<SChapter> {
        val comicPage = client.get(comicUrl, Fetch.DOCUMENT).asJsoup()
        // some menu links skip the comic's first page
        var pageUrl = comicPage.firstPageUrl() ?: comicUrl
        var document = if (pageUrl == comicUrl) comicPage else client.get(pageUrl, Fetch.DOCUMENT).asJsoup()
        val chapters = mutableListOf<SChapter>()
        while (true) {
            chapters += SChapter.create().apply {
                url = pageUrl
                name = document.selectFirst("#comic img")!!.attrOrNull("alt")!!
            }
            pageUrl = document.selectFirst(".comic-nav-next")?.attrOrNull("abs:href") ?: return chapters
            document = client.get(pageUrl, Fetch.DOCUMENT).asJsoup()
        }
    }

    // "First" is a link on every page but the first
    private fun Document.firstPageUrl(): String? = selectFirst("a.comic-nav-first")?.absUrl("href")

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.url.startsWith(baseUrlAlfie)) return fetchAlfiePages(chapter.url)

        val document = client.get(chapter.url, Fetch.DOCUMENT).asJsoup()
        return listOf(Page(0, imageUrl = document.selectFirst("#comic img")!!.absUrl("src")))
    }

    // Chapter archives list 10 comics per page, newest first
    private suspend fun fetchAlfiePages(firstComicUrl: String): List<Page> = coroutineScope {
        val firstComic = client.get(firstComicUrl, Fetch.DOCUMENT).asJsoup()
        val archiveUrl = firstComic.selectFirst(".comic-chapter a")!!.absUrl("href").toHttpUrl()
        val firstArchive = client.get(archiveUrl, Fetch.DOCUMENT).asJsoup()
        // single-page archives have no page navigation
        val lastPage = firstArchive.selectFirst(".paginav-pages")?.text()?.substringAfter(" of ")?.toInt() ?: 1
        val otherArchives = (2..lastPage).map { page ->
            async {
                val pageUrl = archiveUrl.newBuilder().addPathSegments("page/$page").build()
                client.get(pageUrl, Fetch.DOCUMENT).asJsoup()
            }
        }

        (listOf(firstArchive) + otherArchives.awaitAll())
            .flatMap { it.select(".comic-thumbnail-in-archive img") }
            // wide images there are WordPress's resized copy of the original
            .map { it.absUrl("src").replace(IMAGE_SIZE_SUFFIX_REGEX, "") }
            .asReversed()
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    private suspend fun fetchAllComics(): List<SManga> {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        // CYOA and Comics menus; the old Alfie chapter there is skipped for the full Alfie
        val comics = document.select("#menu-item-331, #menu-item-38")
            .select(".menu-item-type-custom a[href]")
            .filterNot { it.text().contains(ALFIE) }
            .map { link ->
                SManga.create().apply {
                    setUrlWithoutDomain(link.absUrl("href"))
                    title = link.text()
                    author = INCASE
                    artist = INCASE
                    status = SManga.COMPLETED
                    genre = "NSFW"
                    thumbnail_url = TextInterceptorHelper.createUrl(title, "")
                }
            }
        return listOf(alfieManga()) + comics
    }

    private fun alfieManga() = SManga.create().apply {
        url = baseUrlAlfie
        title = ALFIE
        author = INCASE
        artist = INCASE
        genre = "fantasy, NSFW"
        thumbnail_url = TextInterceptorHelper.createUrl(ALFIE, "")
    }

    companion object {
        private const val ALFIE = "Alfie"
        private const val INCASE = "InCase"
        private val CHAPTER_NUMBER_REGEX = Regex("""\d+(?:\.\d+)?""")
        private val IMAGE_SIZE_SUFFIX_REGEX = Regex("""-\d+x\d+(?=\.\w+$)""")
    }
}
