package eu.kanade.tachiyomi.extension.all.historyofhyrule

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.textOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

@Source
abstract class HistoryOfHyrule : KeiSource() {

    // The site lists its publications by category, without dates
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // The site has no search: the category lists are searched by title
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val categories = filters.firstInstanceOrNull<CategoryFilter>()?.category?.let(::listOf) ?: CATEGORIES.map { it.second }
        val mangas = categories.flatMap { getCategory(it) }
            .distinctBy { it.url }
            .filter { it.title.contains(query.trim(), ignoreCase = true) }
        return MangasPage(mangas, false)
    }

    // The category lists, in any of their orders
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val path = sitePath(url) ?: return super.getMangasByUrl(url, page)
        val category = path.getOrNull(1)?.let { CATEGORY_PAGE.matchEntire(it) }?.groupValues?.get(1)
        if (path.firstOrNull() != "publications" || CATEGORIES.none { it.second == category }) return super.getMangasByUrl(url, page)
        return MangasPage(getCategory(category!!), false)
    }

    // A publication's links: its cover and info page, and each page of its reader
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val path = sitePath(url) ?: return null
        if (path.size < 2 || path[0] != "publications" || '.' in path[1]) return null
        val manga = SManga.create().apply { this.url = "/publications/${path[1]}/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // The info page gives the details, the publication itself and its versions in other languages
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga).toHttpUrl().resolve("index.html")!!, Fetch.DOCUMENT).asJsoup()
        val fields = document.select("#info p, #contrib p").flatMap { it.fields() }
        val version = document.select("#info p strong").firstOrNull { "version" in it.text().lowercase() }
            ?.text()?.let { VERSION_HEADING.matchEntire(it)?.groupValues?.get(1) ?: it }
        val details = SManga.create().apply {
            url = manga.url
            title = fields.first { it.first == "Title" }.second
            author = fields.firstOrNull { AUTHOR_LABEL.containsMatchIn(it.first) }?.second
            description = MarkdownDescription.Builder()
                .info(version)
                .apply {
                    fields.filterNot { it.first == "Title" || AUTHOR_LABEL.containsMatchIn(it.first) }
                        .forEach { (label, value) -> field(label, value) }
                }
                .build()
            thumbnail_url = document.selectFirst("img[src^=pages/]")?.absUrl("src") ?: manga.thumbnail_url
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        val publication = SChapter.create().apply {
            url = manga.url
            name = version ?: "Chapter"
        }
        val otherLanguages = document.select("a:has(img[src*=/extras/flags/])").mapNotNull { link ->
            val folder = publicationFolder(link.absUrl("href")) ?: return@mapNotNull null
            val language = LANGUAGE_LINK.find(link.closest("p")?.text() ?: link.text())?.groupValues?.get(1) ?: return@mapNotNull null
            SChapter.create().apply {
                url = "/publications/$folder/"
                name = "$language Language Version"
            }
        }

        return SMangaUpdate(details, (listOf(publication) + otherLanguages).distinctBy { it.url })
    }

    // The pages are in the publication's "pages" folder, which the server lists: the front cover, the numbered pages
    // (a blank 001.png pads the two-page spreads of some, and is left out when there is a 001.jpg), then the back cover
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pagesUrl = getChapterUrl(chapter).toHttpUrl().resolve("pages/")!!
        val files = client.get(pagesUrl, Fetch.DOCUMENT).asJsoup().select("a[href]").map { it.attr("href") }
        val numbered = files.mapNotNull { PAGE_FILE.matchEntire(it) }
            .sortedWith(compareBy({ it.groupValues[1].toInt() }, { it.groupValues[2] }, { it.groupValues[3].lowercase() == "png" }))
            .distinctBy { it.groupValues[1].toInt() to it.groupValues[2] }
            .map { it.value }
        val pages = listOfNotNull(files.firstOrNull { COVER_FILE.matches(it) }) + numbered + listOfNotNull(files.firstOrNull { BACK_FILE.matches(it) })
        return pages.mapIndexed { index, file -> Page(index, imageUrl = pagesUrl.resolve(file)!!.toString()) }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    private suspend fun getCategory(category: String) = client.get("$baseUrl/publications/${category}_index.php", Fetch.DOCUMENT).asJsoup()
        // The "Unobtained" section lists what the site is still looking for
        .apply { select("p:has(a[name=unobtained]) + table").remove() }
        .select("a:has(img[src*=thumb])")
        .mapNotNull { link ->
            val folder = publicationFolder(link.absUrl("href")) ?: return@mapNotNull null
            SManga.create().apply {
                url = "/publications/$folder/"
                // The title is in the row under the cover
                title = link.closest("tr")?.nextElementSibling()?.textOrNull() ?: return@mapNotNull null
                thumbnail_url = link.selectFirst("img")?.absUrl("src")
            }
        }
        .distinctBy { it.url }

    // The site shows its pages in a frame of its home page, as index.php?frame=<page>
    private fun sitePath(url: HttpUrl): List<String>? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val page = url.queryParameter("frame")?.let { baseUrl.toHttpUrl().resolve(it) } ?: url
        return page.pathSegments.filter { it.isNotEmpty() }
    }

    // Links to a publication's folder or to one of its pages: /publications/<folder>/[<page>.html]
    private fun publicationFolder(link: String): String? {
        val url = link.toHttpUrl()
        val segments = url.pathSegments
        val page = segments.getOrNull(2)
        val isPublication = url.host == baseUrl.toHttpUrl().host && segments.size in 2..3 && segments[0] == "publications" &&
            '.' !in segments[1] && (page == null || page.isEmpty() || page.endsWith(".html"))
        return if (isPublication) segments[1] else null
    }

    // "<strong>Label:</strong> value" fields, several in one paragraph for the credits
    private fun Element.fields(): List<Pair<String, String>> {
        val fields = mutableListOf<Pair<String, MutableList<String>>>()
        for (node in childNodes()) {
            when {
                node is Element && node.tagName() == "strong" -> fields += node.text().removeSuffix(":").trim() to mutableListOf()
                node is Element -> fields.lastOrNull()?.second?.add(node.text())
                node is TextNode -> fields.lastOrNull()?.second?.add(node.text())
            }
        }
        return fields.map { (label, parts) -> label to parts.joinToString("").trim().removePrefix(":").trim() }
            .filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
    }

    companion object {
        private val CATEGORY_PAGE = Regex("""([a-z0-9]+)_index(?:_\w+)?\.php""")
        private val VERSION_HEADING = Regex("""(?:The|An)\s+(.+?)[!.]*""")
        private val LANGUAGE_LINK = Regex("""(?:in|for the) ([A-Z][a-z]+)""")
        private val AUTHOR_LABEL = Regex("""^(?:Artist or Circle|Artist|Author|Circle)\b""")
        private val PAGE_FILE = Regex("""(\d+)([a-z]?)\.(jpe?g|png|gif)""", RegexOption.IGNORE_CASE)
        private val COVER_FILE = Regex("""cover\.(?:jpe?g|png|gif)""", RegexOption.IGNORE_CASE)
        private val BACK_FILE = Regex("""back\.(?:jpe?g|png|gif)""", RegexOption.IGNORE_CASE)
    }
}
