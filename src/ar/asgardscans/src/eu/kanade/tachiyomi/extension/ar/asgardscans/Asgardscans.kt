package eu.kanade.tachiyomi.extension.ar.asgardscans

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Asgardscans : KeiSource() {

    private suspend fun getMangaList(page: Int, params: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("manga")
            .addQueryParameter("page", page.toString())
            .apply(params).build()

        val document = client.get(url).asJsoup()
        val manga = document.select(".grid a.group").map { el ->
            SManga.create().apply {
                setUrlWithoutDomain(el.absUrl("href"))
                title = el.selectFirst("h3")!!.text()
                thumbnail_url = el.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(manga, document.selectFirst("a[rel=next]") != null)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page) { addQueryParameter("sort", "popular") }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page) { addQueryParameter("sort", "") }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaList(page) {
        addQueryParameter("q", query)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        return getMangaUpdate(SManga.create().apply { this.url = "/manga/$slug" }, emptyList(), true, false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl${manga.url}").asJsoup()

        val updatedManga = parseDetails(document, manga)
        val updatedChapters = parseChapters(document)

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private fun parseDetails(document: Document, manga: SManga): SManga {
        val series = document.select("script[type=application/ld+json]")
            .map { it.data() }
            .firstOrNull { "\"CreativeWorkSeries\"" in it }
            ?.let { JSONObject(it) }

        return manga.apply {
            title = document.selectFirst("h1")!!.text()
            thumbnail_url = document.selectFirst("img[itemprop=image]")?.attr("abs:src")
            description = series?.optString("description")
                ?.takeIf { it.isNotBlank() }
                ?.let { desc ->
                    val alt = series.optString("alternateName")
                    if (alt.isNotBlank()) "$desc\n\nAlternative: $alt" else desc
                }
            genre = document.select("a[href^=/manga?genres=]").joinToString { it.text() }
            status = document.selectFirst("span:containsOwn(الحالة) + span")?.text().toStatus()
        }
    }

    private fun String?.toStatus() = when (this) {
        "مستمر" -> SManga.ONGOING
        "مكتمل" -> SManga.COMPLETED
        "متوقف" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select("ol > li > a[href]").map { element ->
        SChapter.create().apply {
            name = element.selectFirst("div[dir=rtl] span")!!.text()
            setUrlWithoutDomain(element.absUrl("href"))
            date_upload = dateFormat.tryParseDate(
                element.selectFirst("span.tabular")?.text(),
            )
        }
    }

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/${chapter.url}"

    private val pageRegex = Regex("""imageUrl:"([^"]+)["]""")

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl/${chapter.url}").asJsoup()

        val script = document.select("script:not([src])")
            .map { it.data() }
            .firstOrNull { "pages:" in it && "/api/public/page/" in it }
            ?: return emptyList()

        return pageRegex.findAll(script)
            .mapIndexed { i, m ->
                Page(i, imageUrl = "$baseUrl${m.groupValues[1]}")
            }.toList()
    }
}
