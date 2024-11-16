package eu.kanade.tachiyomi.extension.pt.fenixmanhwas

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaDto
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaEntryDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

class FenixManhwas : ZeistManga(
    "Fênix Manhwas",
    "https://fenixleitura.blogspot.com",
    "pt-BR",
) {
    override val supportsLatest = false

    override fun popularMangaRequest(page: Int) = latestUpdatesRequest(page)

    override fun popularMangaParse(response: Response) = latestUpdatesParse(response)

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst(".thum")?.attr("style")?.imgAttr()
        genre = document.select("a[rel=tag]").joinToString { it.text() }
        setUrlWithoutDomain(document.location())
    }

    override fun getChapterFeedUrl(doc: Document): String {
        val feed = doc.selectFirst(".chapter_get")!!.attr("data-labelchapter")
        return apiUrl(chapterCategory)
            .addPathSegments(feed)
            .addQueryParameter("max-results", maxChapterResults.toString())
            .build().toString()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = "$baseUrl/feeds/posts/default/-/Chapter?alt=json"
        val chapterHeaders = headers.newBuilder()
            .set("Referer", "$baseUrl${chapter.url}")
            .build()
        return GET(url, chapterHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterUrl = response.request.headers.get("Referer")!!
        val chapterRef = chapterUrl
            .substringAfterLast("/")
            .substringBeforeLast(".")

        val result = json.decodeFromString<ZeistMangaDto>(response.body.string())
        val mangaEntryDto: ZeistMangaEntryDto = result.feed?.entry
            ?.firstOrNull { it.url?.firstOrNull { link -> link.href.contains(chapterRef, true) } != null }
            ?: throw Exception("Páginas não encontradas")

        return mangaEntryDto.content!!.t.pagesURL().mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    private fun String.pagesURL(): List<String?> {
        val regex = """src="(?<url>[^"]*)"""".toRegex()
        val matches = regex.findAll(this)
        return matches.map { it.groups["url"]?.value }.toList()
    }

    private fun String.imgAttr(): String? {
        val regex = """url\("(?<url>[^"]+)"\)""".toRegex()
        val matchResult = regex.find(this)
        return matchResult?.groups?.get("url")?.value
    }
}
