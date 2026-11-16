package eu.kanade.tachiyomi.extension.es.animebbg

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class AnimeBbg : KeiSource() {

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    // The catalogue's own order: recently updated first
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/obras/".toHttpUrl().newBuilder()
            .addQueryParameter("order", "updated")
            .addQueryParameter("page", page.toString())
            .build()
        return parseList(getDocument(url))
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/obras/buscar/".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            filters.filterIsInstance<FacetGroup>().forEach { group ->
                group.checked.forEach { addQueryParameter("${group.parameter}[]", it) }
            }
            addQueryParameter("order", filters.firstInstanceOrNull<SortFilter>()?.value ?: "follows")
            // Works for adults are only searched with this, once the site's +18 switch is on
            addQueryParameter("adult", "1")
            addQueryParameter("page", page.toString())
        }.build()
        return parseList(getDocument(url))
    }

    // Lists of a genre, from any of their pages
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (!isSiteUrl(url) || segments.firstOrNull() != "genero" || segments.size != 2) return super.getMangasByUrl(url, page)
        val firstPage = url.queryParameter("page")?.toIntOrNull() ?: 1
        val listUrl = url.newBuilder().setQueryParameter("page", (firstPage + page - 1).toString()).build()
        return parseList(getDocument(listUrl))
    }

    // Work links, and chapter links, which start with their work
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (!isSiteUrl(url) || segments.size < 2 || segments[0] != "obras" || !WORK_SEGMENT.matches(segments[1])) return null
        val manga = SManga.create().apply { this.url = "/obras/${segments[1]}/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // The work page has the details and the first 100 chapters; the other chapters come 100 at a time
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = getDocument(getMangaUrl(manga).toHttpUrl())
        if (!fetchChapters) return SMangaUpdate(document.toSManga(manga.url), emptyList())
        val chapterList = document.select("li.abbgChRow").map { it.toSChapter() }.toMutableList()
        var page = 1
        var next = document.selectFirst("a.pageNav-jump--next")
        while (next != null) {
            page++
            val paneUrl = getMangaUrl(manga).toHttpUrl().resolve("capitulos")!!.newBuilder()
                .addQueryParameter("page", page.toString())
                .build()
            val pane = getDocument(paneUrl)
            chapterList += pane.select("li.abbgChRow").map { it.toSChapter() }
            next = pane.selectFirst("a.pageNav-jump--next")
        }

        return SMangaUpdate(document.toSManga(manga.url), chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = getDocument(getChapterUrl(chapter).toHttpUrl())
        .select("img.abbgReader-img")
        .mapIndexed { index, image -> Page(index, imageUrl = image.absUrl("src")) }

    override fun getFilterList(data: JsonElement?) = getFilters()

    private fun parseList(document: Document): MangasPage {
        val mangas = document.select("a.abbgBib-row-link, a.search-comic-link").map { link ->
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = link.closest(".abbgBib-row, .search-comic")?.selectFirst("img")?.absUrl("src")
            }
        }
        // The pager has no next link on the last page, and pages past it show the last page again
        return MangasPage(mangas, document.selectFirst("a.pageNav-jump--next") != null)
    }

    private fun Document.toSManga(url: String) = SManga.create().apply {
        this.url = url
        title = selectFirst("h1.abbgFicha-title")!!.text()
        val credits = select(".abbgFicha-author .abbgCredit-pair").associate { pair ->
            pair.selectFirst(".u-muted")!!.text().removePrefix("·").trim().removeSuffix(":") to pair.select("a").joinToString { it.text() }
        }
        author = credits["Guionista"] ?: credits["Autor"]
        artist = credits["Dibujante"]
        genre = (
            listOfNotNull(selectFirst(".abbgFicha-badges .abbgFlagWrap")?.attr("title")) +
                select(".abbgFicha-badges .abbgChip:not(.abbgChip--status)").map { it.text() } +
                select(".abbgFicha-chips a").map { it.text() }
            ).filter { it.isNotBlank() }.distinct().joinToString().ifEmpty { null }
        status = when (selectFirst(".abbgFicha-badges .abbgChip--status")?.classNames()?.firstOrNull { it.startsWith("is-") }) {
            "is-ongoing" -> SManga.ONGOING
            "is-completed" -> SManga.COMPLETED
            "is-hiatus" -> SManga.ON_HIATUS
            "is-cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        val rating = select(".abbgStats-item").firstOrNull { it.selectFirst(".abbgStats-lbl")?.text() == "Valoración" }
            ?.selectFirst(".abbgStats-num")?.text()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 }
        description = MarkdownDescription.Builder()
            .rating(rating, max = 5)
            .description(selectFirst(".abbgFicha-synopsis .bbWrapper"))
            .altTitles(select(".abbgFicha-heroSub span").map { it.text() })
            .build()
        thumbnail_url = selectFirst(".abbgFicha-cover img")?.absUrl("src")
    }

    private fun Element.toSChapter() = SChapter.create().apply {
        val link = selectFirst("a.abbgChRow-link")!!
        setUrlWithoutDomain(link.absUrl("href"))
        val number = selectFirst(".abbgChRow-num")!!.ownText()
        val chapterTitle = selectFirst(".abbgChRow-title")?.ownText()?.ifBlank { null }
        name = if (chapterTitle != null) "$number: $chapterTitle" else number
        chapter_number = CHAPTER_NUMBER.find(number)?.value?.toFloatOrNull() ?: -1f
        date_upload = Instant.tryParse(selectFirst("time.abbgChRow-time")?.attr("datetime"))
        scanlator = selectFirst(".abbgChRow-secondary a[href*=/grupos/]")?.textOrNull()
    }

    // The site hides works for adults until its +18 switch is on, which it keeps in the session:
    // turn it on as its age question does, then load the page again
    private suspend fun getDocument(url: HttpUrl): Document {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst("form.abbg18[data-encendido=0]") == null) return document
        val token = client.cookieJar.loadForRequest(url).firstOrNull { it.name == "xf_csrf" }?.value ?: return document
        val body = FormBody.Builder()
            .add("_xfToken", token)
            .add("accion", "encender")
            .add("confirmar", "1")
            .add("_xfResponseType", "json")
            .build()
        client.post("$baseUrl/obras/contenido-adulto/", headers, Fetch.XHR, body).close()
        return client.get(url, Fetch.DOCUMENT).asJsoup()
    }

    private fun isSiteUrl(url: HttpUrl) = url.host.removePrefix("www.") == baseUrl.toHttpUrl().host

    companion object {
        private val WORK_SEGMENT = Regex("""[^.]+\.\d+""")
        private val CHAPTER_NUMBER = Regex("""\d+(?:\.\d+)?""")
    }
}
