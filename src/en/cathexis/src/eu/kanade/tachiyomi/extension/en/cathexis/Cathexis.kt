package eu.kanade.tachiyomi.extension.en.cathexis

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class Cathexis : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(fetchComics().map { it.manga }, false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = fetchComics()
            .filter { comic -> comic.names.any { it.contains(query.trim(), ignoreCase = true) } }
            .map { it.manga }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.topPrivateDomain() != baseUrl.toHttpUrl().topPrivateDomain()) return null
        val path = "/${url.pathSegments.first()}"
        val mangas = fetchComics().map { it.manga }
        mangas.find { it.url == path }?.let { return it }
        val sexTherapy = mangas.first { it.url == SEX_THERAPY_PATH }
        val chapterPaths = client.get(getMangaUrl(sexTherapy), Fetch.DOCUMENT).asJsoup()
            .chapterButtons()
            .filter { it.hasAttr("href") }
            .map { it.absUrl("href").toHttpUrl().encodedPath }
        return sexTherapy.takeIf { path in chapterPaths }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        // A series page lists its chapters as buttons, a fan comic page is the comic itself
        val chapterButtons = document.chapterButtons()
        val chapterLinks = chapterButtons.filter { it.hasAttr("href") }
        val details = manga.apply {
            title = document.pageTitles().getValue(url.removePrefix("/"))
            author = AUTHOR
            artist = AUTHOR
            if (chapterLinks.isEmpty()) {
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            } else if (chapterLinks.size < chapterButtons.size) {
                // Upcoming chapters are buttons without a link
                status = SManga.ONGOING
            }
        }
        val chapterList = if (chapterLinks.isEmpty()) {
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "Chapter"
                },
            )
        } else {
            // Button order differs between the site's mobile and desktop layouts
            chapterLinks
                .map { link ->
                    SChapter.create().apply {
                        setUrlWithoutDomain(link.absUrl("href"))
                        name = link.text()
                    }
                }
                .sortedByDescending { NUMBER_REGEX.find(it.name)?.value?.toFloat() }
        }
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("#PAGES_CONTAINER .wixui-image img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.mediaUrl()) }

    // Sex Therapy isn't on the Fan Comics page, its title still comes from the page map
    private suspend fun fetchComics(): List<Comic> {
        val document = client.get("$baseUrl/fan-comics", Fetch.DOCUMENT).asJsoup()
        val titles = document.pageTitles()
        val sexTherapy = SManga.create().apply {
            url = SEX_THERAPY_PATH
            title = titles.getValue(SEX_THERAPY_PATH.removePrefix("/"))
            thumbnail_url = SEX_THERAPY_COVER
        }
        val fanComics = document.select(".wixui-repeater__item:has(a.wixui-button[href])").mapNotNull { item ->
            val link = item.selectFirst("a.wixui-button")!!
            val path = link.absUrl("href").toHttpUrl().encodedPath
            val title = titles[path.removePrefix("/")] ?: return@mapNotNull null
            val manga = SManga.create().apply {
                url = path
                this.title = title
                thumbnail_url = item.selectFirst("img")?.mediaUrl()
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            }
            Comic(manga, listOf(title, link.text()))
        }
        return listOf(Comic(sexTherapy, listOf(sexTherapy.title))) + fanComics
    }

    private fun Document.chapterButtons() = select(".wixui-repeater__item .wixui-button")

    private fun Document.pageTitles(): Map<String, String> = selectFirst("script#wix-viewer-model")!!.data()
        .parseAs<ViewerModel>()
        .siteFeaturesConfigs.router.pagesMap.values
        .associate { it.pageUriSEO to it.title.trim() }

    // Original upload, not the resized copy in src
    private fun Element.mediaUrl() = MEDIA_URL.toHttpUrl().newBuilder()
        .addPathSegment(absUrl("src").toHttpUrl().pathSegments[1])
        .build()
        .toString()

    private class Comic(val manga: SManga, val names: List<String>)

    companion object {
        private const val AUTHOR = "Cathexis"
        private const val SEX_THERAPY_PATH = "/sextherapy"
        private const val MEDIA_URL = "https://static.wixstatic.com/media"
        private const val SEX_THERAPY_COVER =
            "$MEDIA_URL/b0c0d6_ee71b25cbc554d6596e29122c078d1a4~mv2_d_4302_6072_s_4_2.jpg/v1/fit/w_600,h_900,q_90/cover.jpg"
        private val NUMBER_REGEX = Regex("""\d+(\.\d+)?""")
    }
}
