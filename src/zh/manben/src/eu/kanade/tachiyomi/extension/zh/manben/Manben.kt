package eu.kanade.tachiyomi.extension.zh.manben

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.unpacker.Unpacker
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document

@Source
abstract class Manben : KeiSource() {

    private val apiHeaders get() = headersBuilder().set("X-Requested-With", "XMLHttpRequest").build()

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, sort = "4")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page, sort = "2")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) {
            return getMangaList(
                page,
                sort = filters.firstInstance<SortFilter>().value,
                status = filters.firstInstance<StatusFilter>().value,
                genre = filters.firstInstance<GenreFilter>().value,
                reader = filters.firstInstance<ReaderFilter>().value,
                area = filters.firstInstance<AreaFilter>().value,
            )
        }
        val body = FormBody.Builder()
            .add("t", "7")
            .add("pageindex", page.toString())
            .add("title", query.trim())
            .add("f", "0")
            .build()
        return postMangaList("$baseUrl/pagerdata.ashx", "/search", body)
    }

    private suspend fun getMangaList(
        page: Int,
        sort: String,
        status: String = "0",
        genre: String = "0",
        reader: String = "0",
        area: String = "0",
    ): MangasPage {
        val body = FormBody.Builder()
            .add("t", "2")
            .add("pageindex", page.toString())
            .add("sc", "1")
            .add("tsort", sort)
            .add("tagid", genre)
            .add("tst", status)
            .add("tarea", area)
            .add("tgroup", reader)
            .add("tnm", "")
            .build()
        return postMangaList("$baseUrl/mh-list/pagerdata.ashx", "/mh-list/", body)
    }

    private suspend fun postMangaList(url: String, page: String, body: FormBody): MangasPage {
        val mangas = client.post(url, apiHeaders, Fetch.XHR.from(page), body).parseAs<List<MangaDto>>()
        return MangasPage(mangas.map { it.toSManga() }, mangas.size >= PAGE_SIZE)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        var document = client.get(baseUrl.toHttpUrl().newBuilder().encodedPath(url.encodedPath).build(), Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst(DETAILS_SELECTOR) == null) {
            val mangaPath = document.selectFirst("script:containsData(referUrl)")?.data()
                ?.substringAfter("referUrl = \"")?.substringBefore('"')
                ?: return null
            document = client.get(baseUrl.toHttpUrl().resolve(mangaPath) ?: return null, Fetch.DOCUMENT).asJsoup()
        }
        return SManga.create().apply {
            setUrlWithoutDomain(document.location())
            parseDetails(document)
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        manga.parseDetails(document)
        return SMangaUpdate(manga, parseChapterList(document))
    }

    // The status is only in the catalogue, the series page has none
    private fun SManga.parseDetails(document: Document) {
        val details = document.selectFirst(DETAILS_SELECTOR)!!
        val fields = details.select("p.subtitle")
        title = details.selectFirst("p.title")!!.text()
        thumbnail_url = details.selectFirst("img.cover")?.absUrl("src")
        author = fields.firstOrNull { it.text().startsWith("作者") }?.wholeText()?.substringAfter('：')
            ?.split(AUTHOR_SEPARATOR)?.filter { it.isNotBlank() }?.joinToString()?.ifEmpty { null }
        genre = fields.firstOrNull { it.text().startsWith("类型") }?.text()?.substringAfter('：')
            ?.split('/')?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString()?.ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(document.selectFirst("div.detailContent > p")?.text())
            .build()
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        val groups = document.select("ul.detailSelectBar > li").map { it.ownText() }
        return document.select("div.chapter.swiper-slide").flatMapIndexed { i, group ->
            group.select("ul.chapterList > li > a").reversed().map {
                SChapter.create().apply {
                    setUrlWithoutDomain(it.absUrl("href"))
                    name = it.text()
                    scanlator = groups.getOrNull(i)
                }
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val script = document.selectFirst("script:containsData(newImgs)")!!.data()
        val images = Unpacker.unpack(script).substringAfter("newImgs=").substringBefore(";").parseAs<List<String>>()
        return images.mapIndexed { i, image -> Page(i, url = document.location(), imageUrl = image) }
    }

    // The image server checks that the Referer is the chapter page
    override fun getImageRequest(page: Page): Request = Request.Builder()
        .url(page.imageUrl!!)
        .headers(headers.newBuilder().set("Referer", page.url).build())
        .build()

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("搜索关键词时筛选无效"),
        SortFilter(),
        StatusFilter(),
        GenreFilter(),
        ReaderFilter(),
        AreaFilter(),
    )

    companion object {
        private const val PAGE_SIZE = 10
        private const val DETAILS_SELECTOR = "div.detailTop"
        private val AUTHOR_SEPARATOR = Regex("""\s{2,}""")
    }
}
