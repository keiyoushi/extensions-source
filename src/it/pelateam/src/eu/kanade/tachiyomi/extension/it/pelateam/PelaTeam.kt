package eu.kanade.tachiyomi.extension.it.pelateam

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class PelaTeam : KeiSource() {

    // The site only has its list of series
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = MangasPage(fetchSeries(), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = MangasPage(fetchSeries().filter { it.title.contains(query.trim(), ignoreCase = true) }, false)

    private suspend fun fetchSeries(): List<SManga> = client.get(readerUrl, Fetch.DOCUMENT).asJsoup()
        .select("div.listed")
        .map { parseSeries(it) }

    // Series are only known by their name, in the "manga" parameter of every link
    override fun getMangaUrl(manga: SManga) = readerUrl.newBuilder().addQueryParameter("manga", manga.url).build().toString()

    // Details and chapters are on the same page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val series = document.selectFirst("div.listed2")!!
        // Only the latest chapter has a date, on the series card
        val latest = series.selectFirst(".riqcap12")
        val latestName = latest?.selectFirst("a")?.text()?.substringAfter(':')?.trim()
        val latestDate = DATE_FORMAT.tryParseDate(latest?.selectFirst("c1")?.text(), ZONE)
        // The English and Japanese links lead to other sites
        val chapterList = document.select("a[title=Capitolo Italiano]").mapIndexed { i, link ->
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = link.selectFirst("b")!!.text()
                chapter_number = NUMBER_REGEX.find(name)?.value?.toFloat() ?: -1f
                if (i == 0 && name == latestName) date_upload = latestDate
            }
        }
        return SMangaUpdate(parseSeries(series), chapterList)
    }

    private fun parseSeries(card: Element) = SManga.create().apply {
        url = card.selectFirst(".nomeserie a")!!.absUrl("href").toHttpUrl().queryParameter("manga")!!
        title = card.selectFirst(".nomeserie a")!!.text()
        thumbnail_url = card.selectFirst("a.thumb img")?.absUrl("src")
        val info = card.selectFirst(".riqtesto:has(b)")
        author = info?.value("Storia")
        artist = info?.value("Disegni")
        status = when (info?.value("Stato")) {
            "In corso" -> SManga.ONGOING
            "Concluso" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(card.selectFirst(".riqtesto:not(:has(b))"))
            .build()
    }

    // "<b>Storia:</b> ONE <br>"
    private fun Element.value(label: String) = (selectFirst("b:containsOwn($label)")?.nextSibling() as? TextNode)?.text()?.trim()?.ifEmpty { null }

    // A chapter link leads to its series; an unknown series gets the list of series
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val manga = SManga.create().apply { this.url = url.queryParameter("manga") ?: return null }
        val series = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup().selectFirst("div.listed2") ?: return null
        return parseSeries(series).apply { initialized = true }
    }

    // The reader lists every page in imageArray, relative to its own address
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val script = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup().selectFirst("script:containsData(imageArray)") ?: return emptyList()
        val base = chapterUrl.toHttpUrl()
        return IMAGE_REGEX.findAll(script.data()).mapIndexed { i, match ->
            Page(i, imageUrl = base.resolve(match.groupValues[1])!!.toString())
        }.toList()
    }

    private val readerUrl get() = "$baseUrl/Computer/".toHttpUrl()

    companion object {
        private val IMAGE_REGEX = Regex("""imageArray\[\d+]\s*=\s*'([^']+)'""")
        private val NUMBER_REGEX = Regex("""\d+(?:\.\d+)?""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val ZONE = ZoneId.of("Europe/Rome")
    }
}
