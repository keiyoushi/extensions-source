package eu.kanade.tachiyomi.extension.all.yespornpics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class YesPornPics : KeiSource() {

    // The site has a single gallery list
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = getGalleryList(listUrl(null, page))

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // The search form redirects to /sex/<query, each run of other characters turned into "-">/, the list of a model, paysite or tag
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val slug = query.lowercase().replace(NON_ALPHANUMERIC, "-").trim('-')
        return getGalleryList(listUrl(slug.ifEmpty { null }, page))
    }

    private fun listUrl(slug: String?, page: Int): HttpUrl = baseUrl.toHttpUrl().newBuilder().apply {
        if (slug != null || page > 1) addPathSegment("sex")
        slug?.let(::addPathSegment)
        if (page > 1) addPathSegment(page.toString())
    }.build()

    private suspend fun getGalleryList(url: HttpUrl) = client.get(url, Fetch.DOCUMENT).asJsoup().toMangasPage()

    private fun Document.toMangasPage(): MangasPage {
        val mangas = select("div.gallery > a").map { link ->
            val image = link.selectFirst("img")!!
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = image.attrOrNull("alt")!!.removePrefix("yes! ").removeSuffix(" porn pics")
                thumbnail_url = image.absUrl("src")
            }
        }
        return MangasPage(mangas, select("div.pag a").any { it.ownText() == "Next" })
    }

    // Galleries and the lists of a model, paysite or tag share /sex/<name>/, so the page tells which one a link is
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host || segments.getOrElse(0) { "sex" } != "sex") {
            return MangasPage(emptyList(), false)
        }
        val slug = segments.getOrNull(1)?.takeUnless { it.all(Char::isDigit) }
        val firstPage = segments.lastOrNull()?.toIntOrNull() ?: 1
        val document = client.get(listUrl(slug, firstPage + page - 1), Fetch.DOCUMENT).asJsoup()
        if (slug == null || document.selectFirst(PHOTO_SELECTOR) == null) return document.toMangasPage()
        val manga = document.toSManga("/sex/$slug", null).apply { initialized = true }
        return MangasPage(listOf(manga), false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Gallery"
        }
        return SMangaUpdate(document.toSManga(manga.url, manga.thumbnail_url), listOf(chapter))
    }

    private fun Document.toSManga(url: String, thumbnailUrl: String?): SManga {
        val photos = select(PHOTO_SELECTOR)
        val tags = selectFirst("div.gallerytags")
        return SManga.create().apply {
            this.url = url
            title = selectFirst("title")!!.text().removeSuffix(" yes porn pics xxx")
            author = tags?.select("h5 a")?.joinToString { it.text() }?.ifEmpty { null }
            artist = tags?.selectFirst("h4 a")?.text()?.removeSuffix(" porn pics")
            genre = tags?.select("h6 a")?.joinToString { it.text() }?.ifEmpty { null }
            description = MarkdownDescription.Builder().info("${photos.size} photos").build()
            thumbnail_url = thumbnailUrl ?: photos.firstOrNull()?.selectFirst("img")?.absUrl("src")
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(PHOTO_SELECTOR).mapIndexed { index, link -> Page(index, imageUrl = link.absUrl("href")) }

    companion object {
        // Photos link to the full-size image; the paysite ads among them link elsewhere
        private const val PHOTO_SELECTOR = "div.azmobile > a[href*=/pics/]"
        private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
    }
}
