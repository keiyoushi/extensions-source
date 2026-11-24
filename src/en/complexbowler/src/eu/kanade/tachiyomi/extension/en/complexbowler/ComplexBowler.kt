package eu.kanade.tachiyomi.extension.en.complexbowler

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element

@Source
abstract class ComplexBowler : KeiSource() {

    // The home page is the site's only list: the group's few projects
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = MangasPage(getProjects(), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = MangasPage(getProjects().filter { it.title.contains(query.trim(), ignoreCase = true) }, false)

    // The home page sorts the projects into ongoing and completed ones
    private suspend fun getProjects(): List<SManga> = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        .select(".project-section:has(a > img)")
        .flatMap { section ->
            val status = when (section.selectFirst("h2")?.text()?.lowercase()) {
                "ongoing projects" -> SManga.ONGOING
                "completed projects" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            section.select("a:has(> img)").map { link ->
                SManga.create().apply {
                    setUrlWithoutDomain(link.absUrl("href"))
                    val cover = link.selectFirst("img")!!
                    title = cover.attrOrNull("alt")!!
                    thumbnail_url = cover.absUrl("src")
                    this.status = status
                }
            }
        }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host == baseUrl.toHttpUrl().host && url.encodedPath.removeSuffix(".html") in HOME_PATHS) return getPopularManga(page)
        return super.getMangasByUrl(url, page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val path = url.encodedPath.removeSuffix(".html")
        val projects = getProjects()
        // A chapter link leads to the project whose page lists it
        val project = projects.firstOrNull { it.url == path }
            ?: projects.firstOrNull { project -> getProjectSection(project).select(CHAPTER_SELECTOR).any { it.toSChapter().url == path } }
            ?: return null
        return getMangaUpdate(project, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        // The project's title, cover and status are on the home page; its own page only shows its newest chapter
        val listed = if (fetchDetails) async { getProjects().firstOrNull { it.url == manga.url } } else null
        val section = getProjectSection(manga)
        val project = listed?.await() ?: manga
        val details = SManga.create().apply {
            url = manga.url
            title = project.title
            description = MarkdownDescription.Builder()
                .apply { section.select("> p").forEach { description(it) } }
                .altTitles(listOf(section.selectFirst("h2")!!.text()).filter { it != project.title })
                .build()
            thumbnail_url = project.thumbnail_url
            status = project.status
            update_strategy = if (status == SManga.COMPLETED) UpdateStrategy.ONLY_FETCH_ONCE else UpdateStrategy.ALWAYS_UPDATE
        }
        SMangaUpdate(details, section.select(CHAPTER_SELECTOR).map { it.toSChapter() })
    }

    private suspend fun getProjectSection(manga: SManga) = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup().selectFirst(".project-section")!!

    // Chapters hosted elsewhere keep their full address
    private fun Element.toSChapter() = SChapter.create().apply {
        val link = absUrl("href")
        if (link.toHttpUrl().host == baseUrl.toHttpUrl().host) setUrlWithoutDomain(link) else url = link
        name = text()
    }

    override fun getChapterUrl(chapter: SChapter) = if (chapter.url.startsWith("http")) chapter.url else super.getChapterUrl(chapter)

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = getChapterUrl(chapter).toHttpUrl()
        // The only chapters hosted elsewhere are imgchest albums opened in Cubari: /read/imgchest/<album>/...
        if (url.host != baseUrl.toHttpUrl().host) {
            return client.get("https://imgchest.com/p/${url.pathSegments[2]}", Fetch.DOCUMENT).asJsoup()
                .selectFirst("#app")!!.attr("data-page").parseAs<ImgchestPageDto>()
                .toPages()
        }
        val script = client.get(url, Fetch.DOCUMENT).asJsoup().select("script").map { it.data() }.first { "input_images" in it }
        return URL_REGEX.findAll(IMAGES_REGEX.find(script)!!.groupValues[1])
            .mapIndexed { index, match -> Page(index, imageUrl = match.groupValues[1]) }
            .toList()
    }

    companion object {
        private const val CHAPTER_SELECTOR = ".chapter-list li a"
        private val HOME_PATHS = setOf("/", "/index")
        private val IMAGES_REGEX = Regex("""input_images\s*=\s*\[([^\]]*)]""")
        private val URL_REGEX = Regex(""""(https?://[^"]+)"""")
    }
}
