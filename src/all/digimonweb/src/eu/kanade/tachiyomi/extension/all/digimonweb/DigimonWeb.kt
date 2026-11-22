package eu.kanade.tachiyomi.extension.all.digimonweb

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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class DigimonWeb : KeiSource() {

    // A fixed set of short works, with no dates to sort them by
    override val supportsLatest = false

    // The pages that list this language's works: the Digimon Comic series, Digimon Dreamers and the 2023 award one-shots
    private val catalogPages: List<String>
        get() = when (lang) {
            "ja" -> listOf(COMIC_PATH, DREAMERS_PATH, AWARD_PATH)
            "en" -> listOf("${COMIC_PATH}en/", "${DREAMERS_PATH}en/")
            else -> listOf("${DREAMERS_PATH}cn/")
        }

    override suspend fun getPopularManga(page: Int): MangasPage = coroutineScope {
        val works = catalogPages.map { async { getWorks(it) } }.awaitAll().flatten()
        MangasPage(works.map { it.manga }, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = getPopularManga(page).mangas.filter { it.title.contains(query.trim(), ignoreCase = true) }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val path = url.encodedPath.removeSuffix("/") + "/"
        if (url.isSite() && path in catalogPages && !path.startsWith(DREAMERS_PATH)) {
            return MangasPage(getWorks(path).map { it.manga }, false)
        }
        return super.getMangasByUrl(url, page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.isSite()) return null
        val path = url.encodedPath
        val mangaUrl = listOf(COMIC_URL_REGEX, DREAMERS_URL_REGEX, AWARD_URL_REGEX).firstNotNullOfOrNull { it.find(path)?.value }
        if (mangaUrl == null || mangaUrl.language() != lang) return null
        val manga = SManga.create().apply { this.url = mangaUrl }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // Details and episodes are on the page that lists the work
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val listing = when {
            manga.url.startsWith(COMIC_PATH) -> manga.url.comicListing()
            manga.url.startsWith(AWARD_PATH) -> AWARD_PATH
            else -> manga.url
        }
        val work = getWorks(listing).first { it.manga.url == manga.url }
        return SMangaUpdate(work.manga, work.chapters)
    }

    // The series have no page of their own
    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().resolve(if (manga.url.startsWith(COMIC_PATH)) manga.url.comicListing() else manga.url)!!.toString()

    // The reader lists the pages from the last one
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("img[src*=/img_comic_]")
        .mapNotNull { image -> PAGE_REGEX.find(image.attr("src"))?.let { it.groupValues[1].toInt() to image.absUrl("src") } }
        .sortedBy { it.first }
        .mapIndexed { index, (_, imageUrl) -> Page(index, imageUrl = imageUrl) }

    private suspend fun getWorks(path: String): List<Work> {
        val document = client.get(baseUrl.toHttpUrl().resolve(path)!!, Fetch.DOCUMENT).asJsoup()
        return when {
            path.startsWith(COMIC_PATH) -> document.comicWorks(path)
            path.startsWith(DREAMERS_PATH) -> listOf(document.dreamersWork(path))
            else -> document.awardWorks()
        }
    }

    // Each work has its key visual and episode list; the back number list, which has neither, is left out
    private fun Document.comicWorks(path: String): List<Work> = select("div.pg-works__wrap:has(div.pg-works__kv)").map { work ->
        val episodes = work.select("ul.pg-ep_list a")
        val key = COMIC_KEY_REGEX.find(episodes[0].absUrl("href"))!!.groupValues[1]
        val image = work.selectFirst("div.pg-works__kv img")!!
        val credit = work.closest("div.pg-works")?.selectFirst("p.pg-works_tit__txt")?.text()
        val manga = SManga.create().apply {
            url = "${path}comic/$key/"
            title = image.attr("alt")
            thumbnail_url = image.absUrl("src")
            author = credit?.substringBefore('（')?.trim()
            description = MarkdownDescription.Builder()
                .info(credit?.substringAfter('（', "")?.removeSuffix("）"))
                .description(work.selectFirst(".pg-works_detail__txt")?.text())
                .build()
        }
        Work(manga, episodes.reversed().map { it.toSChapter() })
    }

    private fun Document.dreamersWork(path: String): Work {
        val artist = selectFirst(".pg-tit__explanation--author dd")?.text()
        val manga = SManga.create().apply {
            url = path
            title = title().substringBefore(" | ")
            thumbnail_url = (selectFirst("img[src*=img_comic_vol]") ?: selectFirst("h3.pg-tit img"))?.absUrl("src")
            author = listOfNotNull(selectFirst(".pg-tit__explanation--draft dd")?.text(), artist).joinToString().ifEmpty { null }
            this.artist = artist
            description = MarkdownDescription.Builder()
                .description(selectFirst(".pg-intro__text--01")?.text())
                .build()
        }
        return Work(manga, select("ul.pg-episodesList a").reversed().map { it.toSChapter() })
    }

    // Only the winners can be read; the other finalists have no link
    private fun Document.awardWorks(): List<Work> = select("li.pg-awardList__item:has(div.pg-awardList__kv a)").map { item ->
        val link = item.selectFirst("div.pg-awardList__kv a")!!
        val manga = SManga.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            title = item.selectFirst("div.pg-awardList__tit--01 h5")!!.text()
            thumbnail_url = link.selectFirst("img")?.absUrl("src")
            author = item.selectFirst("div.author")?.text()
            description = MarkdownDescription.Builder()
                .info(item.closest("div.pg-award")?.selectFirst("h4 img")?.attr("alt")?.substringBefore(' '))
                .description(item.selectFirst(".pg-story__content")?.text())
                .build()
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        Work(
            manga,
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "読切"
                },
            ),
        )
    }

    private fun Element.toSChapter() = SChapter.create().apply {
        setUrlWithoutDomain(absUrl("href"))
        name = text()
    }

    private fun HttpUrl.isSite() = host.removePrefix("www.") == baseUrl.toHttpUrl().host

    private fun String.comicListing() = substringBefore("/comic/") + "/"

    private fun String.language() = when {
        contains("/en/") -> "en"
        contains("/cn/") -> "zh"
        else -> "ja"
    }

    private class Work(val manga: SManga, val chapters: List<SChapter>)

    companion object {
        private const val COMIC_PATH = "/digimoncomic/"
        private const val DREAMERS_PATH = "/digimondreamers/"
        private const val AWARD_PATH = "/digimon_comicaward2023/"
        private val COMIC_URL_REGEX = Regex("""^/digimoncomic/(?:en/)?comic/[^/]+/""")
        private val DREAMERS_URL_REGEX = Regex("""^/digimondreamers/(?:(?:en|cn)/)?""")
        private val AWARD_URL_REGEX = Regex("""^/digimon_comicaward2023/comic_\d+/""")
        private val COMIC_KEY_REGEX = Regex("""/comic/([^/]+)/ep_""")
        private val PAGE_REGEX = Regex("""/img_comic_(\d+)\.""")
    }
}
