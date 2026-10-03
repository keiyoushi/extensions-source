package eu.kanade.tachiyomi.extension.en.myadultcomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class MyAdultComics : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/index.php?page=$page").asJsoup()
        return parseMangaList(document)
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("td.list_container").mapNotNull { element ->
            val link = element.selectFirst("p.text_container > a")!!
            val title = link.text()
            val href = link.absUrl("href")
            if (href.isEmpty() || title.isEmpty()) return@mapNotNull null

            val image = element.selectFirst("img.fon_pic_img")

            SManga.create().apply {
                setUrlWithoutDomain(href)
                this.title = title
                thumbnail_url = image?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("td.tbl_page a:contains(>>)") != null

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Use the text search field along with the type below."),
        Filters(),
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val searchType = filters.firstInstanceOrNull<Filters>()?.selectedValue() ?: "title"

        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("index.php")

        if (searchType == "title") {
            if (query.isNotBlank()) {
                url.addQueryParameter("name", query)
            }
            url.addQueryParameter("search", "yes")
        } else {
            url.addQueryParameter("search", query)
            url.addQueryParameter("sort", searchType)
        }
        url.addQueryParameter("page", page.toString())

        val document = client.get(url.build()).asJsoup()
        return parseMangaList(document)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.encodedPath != "/read.php") return null
        val mangaId = url.queryParameter("i") ?: return null

        val document = client.get(url).asJsoup()
        return parseMangaDetails(document).apply {
            setUrlWithoutDomain("$baseUrl/read.php?i=$mangaId")
            initialized = true
        }
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1#TOP")!!.text()
        genre = document.select("p.text_info_book:contains(Tags:) a")
            .joinToString { it.text() }
            .ifEmpty { null }
        artist = document.select("p.text_info_book:contains(Artists:) a")
            .joinToString { it.text() }
            .ifEmpty { null }
        author = artist
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        val chapter = SChapter.create().apply {
            setUrlWithoutDomain(document.location())
            name = "Gallery"
            date_upload = 0L
        }
        return listOf(chapter)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter))
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(let template)")?.data() ?: return emptyList()

        return pageRegex.findAll(script).mapIndexed { index, matchResult ->
            Page(index, imageUrl = "$baseUrl/${matchResult.groupValues[1]}")
        }.toList()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val document = response.asJsoup()
        val details = parseMangaDetails(document)
        val chapterList = parseChapterList(document)
        return SMangaUpdate(details, chapterList)
    }

    private val pageRegex = """src=["'](books/[^"']+)["']""".toRegex()
}
