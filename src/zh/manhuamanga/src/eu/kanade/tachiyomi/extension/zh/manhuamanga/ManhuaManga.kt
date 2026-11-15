package eu.kanade.tachiyomi.extension.zh.manhuamanga

import eu.kanade.tachiyomi.multisrc.mccms.Intl
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSConfig
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSWeb
import eu.kanade.tachiyomi.multisrc.mccms.pcHeaders
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class ManhuaManga : MCCMSWeb() {

    override val config = MCCMSConfig(lazyLoadImageAttr = "data-src")

    // The cover server answers 567 without the site as Referer
    override fun Headers.Builder.configureHeaders(): Headers.Builder = this

    override fun parseListing(document: Document): MangasPage {
        val mangas = document.select(simpleMangaSelector()).map(::simpleMangaFromElement)
        // The last page links to itself as the next one
        val pagination = document.selectFirst(".pagination")
        val next = pagination?.selectFirst("a.next")?.attr("href")
        return MangasPage(mangas, next != null && next != pagination.selectFirst("a.on")?.attr("href"))
    }

    override fun simpleMangaSelector() = ".comic-list .comic-item"

    override fun simpleMangaFromElement(element: Element) = SManga.create().apply {
        val link = element.selectFirst("h3 a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        val path = when {
            url.encodedPath.startsWith("/comic_") -> url.encodedPath
            url.encodedPath.startsWith("/chapter_") -> seriesPath(url) ?: return null
            else -> return null
        }
        val manga = SManga.create().apply { this.url = path }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    private suspend fun seriesPath(chapterUrl: HttpUrl): String? = client.get(chapterUrl, pcHeaders, Fetch.DOCUMENT).asJsoup()
        .select("script[type=application/ld+json]")
        .firstNotNullOfOrNull { it.data().parseAs<LinkedDataDto>().seriesUrl }
        ?.toHttpUrl()
        ?.encodedPath

    // The site has no mobile domain
    override fun getMangaUrl(manga: SManga) = baseUrl.toHttpUrl().resolve(manga.url)!!.toString()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val info = document.selectFirst(".comic-main-info")!!
        val stats = info.select(".stat-item span").map { it.text() }
        val tags = info.select(".comic-tags .tag").map { it.text() }

        setUrlWithoutDomain(document.location())
        title = info.selectFirst("h1")!!.text().removePrefix("《").removeSuffix("》")
        thumbnail_url = document.selectFirst(".comic-cover-large img")?.absUrl("src")
        author = stats.firstNotNullOfOrNull { it.substringAfter("作者：", "").ifEmpty { null } }
        genre = tags.firstOrNull()?.split(' ')?.filter(String::isNotEmpty)?.joinToString()?.ifEmpty { null }
        status = when (tags.getOrNull(1)) {
            "连载", "连载中" -> SManga.ONGOING
            "完结", "已完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .rating(stats.firstNotNullOfOrNull { it.substringAfter("评分：", "").toFloatOrNull() })
            .description(document.selectFirst(".comic-description p")?.text())
            .build()
    }

    override fun chapterListSelector() = "#chapter-list .chapter-item"

    override fun getChapterUrl(chapter: SChapter) = baseUrl.toHttpUrl().resolve(chapter.url)!!.toString()

    override suspend fun fetchFilterData(): JsonElement {
        val genres = fetchGenresPage().select(".filter-options a[href*=/tags/]")
            .map { it.text() to it.attr("href").substringAfterLast('/') }
        return (listOf(Intl.all to "") + genres).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<Pair<String, String>>>())
}
