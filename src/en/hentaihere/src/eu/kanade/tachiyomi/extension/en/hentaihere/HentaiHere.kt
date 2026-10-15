package eu.kanade.tachiyomi.extension.en.hentaihere

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class HentaiHere : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", getFilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/browse/newest/page-$page").asJsoup())

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        // `doujinshi`/`original`/`imageset` are the current detail paths; `m` is the old one, kept so legacy links still resolve.
        val type = url.pathSegments.firstOrNull()
        if (type !in DETAIL_TYPES) return null
        val slug = url.pathSegments.getOrNull(1) ?: return null

        val manga = SManga.create().apply { this.url = "/$type/$slug" }
        return parseMangaDetails(client.get(getMangaUrl(manga)).asJsoup(), manga)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = sortFilterList[filters.firstInstance<SortFilter>().state].first
        val alphabet = alphabetFilterList[filters.firstInstance<AlphabetFilter>().state].first
        val status = statusFilterList[filters.firstInstance<StatusFilter>().state].first
        val category = categoryFilterList[filters.firstInstance<CategoryFilter>().state].first

        val url = if (query.isNotEmpty()) {
            "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .toString()
        } else {
            buildString {
                append(if (category.isNotEmpty()) "/category/${category.removePrefix("t")}" else "/browse")
                if (status.isNotEmpty()) append('/').append(status)
                if (alphabet.isNotEmpty()) append('/').append(alphabet)
                append('/').append(sort)
                if (page > 1) append("/page-").append(page)
            }.let { baseUrl + it }
        }

        return parseMangaList(client.get(url).asJsoup())
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("article.card[data-card]").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                title = element.selectFirst("h3")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val chapterList = document.select("#preview-chapter option").map { option ->
            val name = option.text().removeSuffix("(latest)").trim()
            SChapter.create().apply {
                setUrlWithoutDomain(option.absUrl("data-read"))
                this.name = name
                chapter_number = CHAPTER_NUMBER_REGEX.find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
            }
        }

        return SMangaUpdate(parseMangaDetails(document, manga), chapterList)
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga {
        val details = document.select("dl[aria-label=Details]")
        fun detail(label: String): String? = details.select("dt:contains($label)").firstOrNull()
            ?.nextElementSibling()
            ?.textOrNull()

        return manga.apply {
            title = document.selectFirst("h1#book-title")!!.text()
            author = detail("Artist") ?: detail("Author")
            genre = document.select("ul[aria-label=Tags] a").joinToString { it.text() }
            status = when (detail("Status")?.lowercase()) {
                "completed" -> SManga.COMPLETED
                "ongoing" -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attrOrNull("content")
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .selectFirst("script[data-reader-data]")
        ?.data()
        ?.parseAs<ReaderData>()
        ?.toPageList()
        ?: emptyList()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Filters below are ignored for text search."),
        SortFilter(sortFilterList.map { it.second }.toTypedArray()),
        Filter.Separator(),
        AlphabetFilter(alphabetFilterList.map { it.second }.toTypedArray()),
        Filter.Separator(),
        StatusFilter(statusFilterList.map { it.second }.toTypedArray()),
        Filter.Separator(),
        CategoryFilter(categoryFilterList.map { it.second }.toTypedArray()),
    )

    /** Legacy entries stored the pre-redesign `/m/{id}` path; the site only serves `/doujinshi/{id}` now. */
    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url.replaceFirst(LEGACY_MANGA_URL_REGEX, "/doujinshi/$1")

    companion object {
        private val DETAIL_TYPES = setOf("doujinshi", "original", "imageset", "m")
        private val LEGACY_MANGA_URL_REGEX = Regex("^/m/(\\d+)$")
        private val CHAPTER_NUMBER_REGEX = Regex("""Chapter\s+([\d.]+)""")
    }
}
