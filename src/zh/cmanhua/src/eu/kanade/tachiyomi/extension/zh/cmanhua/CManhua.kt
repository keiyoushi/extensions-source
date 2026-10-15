package eu.kanade.tachiyomi.extension.zh.cmanhua

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class CManhua : KeiSource() {

    // Listing pages are only reachable through ASP.NET postbacks from the previous page
    private var lastListing: Triple<HttpUrl, Int, Document>? = null

    override suspend fun getPopularManga(page: Int): MangasPage = fetchMangaList(browseUrl("views_desc", ""), page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchMangaList(browseUrl("updated_desc", ""), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/Modules/Search/SearchHandler.ashx".toHttpUrl().newBuilder()
                .addQueryParameter("q", query.trim())
                .build()
            val mangas = client.get(url).parseAs<List<SearchDto>>().map {
                SManga.create().apply {
                    this.url = it.slug
                    title = it.title
                    thumbnail_url = baseUrl + it.cover
                }
            }
            return MangasPage(mangas, false)
        }

        val url = browseUrl(
            sort = filters.firstInstance<SortFilter>().toUriPart(),
            status = filters.firstInstance<StatusFilter>().toUriPart(),
        )
        return fetchMangaList(url, page)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val details = SManga.create().apply {
            title = document.selectFirst("#MainContent_lblTitle")!!.text()
            author = document.selectFirst("#MainContent_lblAuthor")?.text()
            status = when (document.selectFirst("#MainContent_lblStatus")?.text()?.lowercase(Locale.ROOT)) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            genre = document.select(".cd-tag").joinToString { it.text() }
            description = buildString {
                document.selectFirst("#MainContent_lblDescription")?.text()?.let(::append)
                document.selectFirst("#MainContent_lblOtherName")?.text()?.takeIf { it.isNotBlank() }?.let {
                    append("\n\nAlternative name: ", it)
                }
            }
            thumbnail_url = document.selectFirst("#MainContent_imgCover")?.absUrl("src")
        }

        if (!fetchChapters) return SMangaUpdate(details, chapters)

        val rangeTargets = document.select("#MainContent_ctl12 a[href*=rptChapterRanges]:not(.btn-primary)")
            .map { POSTBACK_REGEX.find(it.attr("href"))!!.groupValues[1] }
        val documents = listOf(document) + rangeTargets.map { postBack(document, it) }

        val chapterList = documents.flatMap { doc ->
            doc.select("li.cd-chapter-item").map { element ->
                val link = element.selectFirst("a.cd-chapter-link")!!
                val match = CHAPTER_REGEX.find(link.text())
                SChapter.create().apply {
                    url = link.absUrl("href").toHttpUrl().queryParameter("id")!!
                    name = match?.let { "Chapter ${it.groupValues[1]}: ${it.groupValues[2]}" } ?: link.text()
                    chapter_number = match?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
                    date_upload = DATE_FORMAT.tryParseDate(element.selectFirst("span.small")?.text())
                }
            }
        }.distinctBy { it.url }.reversed()

        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select("img.chapter-image")
        .mapIndexed { index, image ->
            Page(index, imageUrl = image.absUrl("data-src").ifEmpty { image.absUrl("src") })
        }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comic/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/ReadComic?id=${chapter.url}"

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Filters are ignored when searching by name."),
        SortFilter(),
        StatusFilter(),
    )

    private fun browseUrl(sort: String, status: String): HttpUrl = "$baseUrl/Browse".toHttpUrl().newBuilder()
        .addQueryParameter("orderBy", sort)
        .apply { if (status.isNotEmpty()) addQueryParameter("status", status) }
        .build()

    private suspend fun fetchMangaList(url: HttpUrl, page: Int): MangasPage {
        val cached = lastListing?.takeIf { it.first == url && it.second <= page }
        var current = cached?.second ?: 1
        var document = cached?.third ?: client.get(url).asJsoup()

        while (current < page) {
            val target = document.nextPageTarget() ?: return MangasPage(emptyList(), false)
            document = postBack(document, target)
            current++
        }
        lastListing = Triple(url, current, document)

        val mangas = document.select("#browseSection a[href^=/comic/]").map { element ->
            SManga.create().apply {
                this.url = element.absUrl("href").toHttpUrl().pathSegments[1]
                title = element.selectFirst(".comic-title")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, document.nextPageTarget() != null)
    }

    private fun Document.nextPageTarget(): String? = select("a[id^=MainContent_rptPager_lnkPage]")
        .firstOrNull { it.text() == ">" }
        ?.let { POSTBACK_REGEX.find(it.attr("href"))?.groupValues?.get(1) }

    private suspend fun postBack(document: Document, target: String): Document {
        val form = document.selectFirst("form#ctl01")!!
        val body = FormBody.Builder().apply {
            add("__EVENTTARGET", target)
            form.select("input[name]").forEach { input ->
                val name = input.attr("name")
                when (input.attr("type")) {
                    "submit", "button", "image", "file" -> Unit
                    "checkbox", "radio" -> if (input.hasAttr("checked")) add(name, input.attr("value"))
                    else -> if (name != "__EVENTTARGET") add(name, input.attr("value"))
                }
            }
            form.select("select[name]").forEach { select ->
                val option = select.selectFirst("option[selected]") ?: select.selectFirst("option")
                add(select.attr("name"), option?.attr("value").orEmpty())
            }
        }.build()

        return client.post(form.absUrl("action"), headers, body).asJsoup()
    }

    @Serializable
    class SearchDto(
        val slug: String,
        val title: String,
        val cover: String,
    )

    companion object {
        private val POSTBACK_REGEX = Regex("""__doPostBack\('([^']+)'""")
        private val CHAPTER_REGEX = Regex("""^\D*?(\d+(?:\.\d+)?)\s*:\s*(.*)$""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    }
}
