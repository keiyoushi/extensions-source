package eu.kanade.tachiyomi.extension.vi.tranh18

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document

@Source
abstract class Tranh18 : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val request = if (page > 1) {
            client.get("$baseUrl/comics?page=$page")
        } else {
            client.get(baseUrl)
        }

        return request.use { parseMangaPage(it) }
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = if (page > 1) "$baseUrl/update?page=$page" else "$baseUrl/update"
        return client.get(url).use { parseMangaPage(it) }
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("search")
                addQueryParameter("keyword", query)
            } else {
                addPathSegment("comics")
                filters.forEach {
                    when (it) {
                        is KeywordList -> addQueryParameter("tag", it.values[it.state].genre)
                        is StatusList -> addQueryParameter("end", it.values[it.state].genre)
                        is GenreList -> addQueryParameter("area", it.values[it.state].genre)
                        else -> {}
                    }
                }
            }
            addQueryParameter("page", page.toString())
        }.build()

        return client.get(url).use { parseMangaPage(it) }
    }

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".box-body ul li, .manga-list ul li").map { element ->
            SManga.create().apply {
                val sel = element.selectFirst(".mh-item, .manga-list-2-cover")!!
                val a = sel.selectFirst("a")!!
                setUrlWithoutDomain(a.absUrl("href"))
                title = a.attr("title")
                thumbnail_url = sel.selectFirst("p.mh-cover")?.attr("style")?.let { style ->
                    if (style.contains("url(")) {
                        baseUrl + style.substringAfter("url(").substringBefore(")")
                    } else {
                        null
                    }
                } ?: (baseUrl + sel.selectFirst("img")?.attr("data-original"))
            }
        }
        val hasNextPage = document.selectFirst(".page-pagination li.active ~ li:not(.disabled) a") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments.isNotEmpty()) {
            val manga = SManga.create().apply { setUrlWithoutDomain(url.toString()) }
            return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }
        return null
    }

    // =========================== Manga Details ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = client.get(getMangaUrl(manga)).use { response ->
        val document = response.asJsoup()
        val details = parseMangaDetails(document)
        val chaptersList = parseChapterList(document)

        SMangaUpdate(details, chaptersList)
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.select(".info h1, .detail-main-info-title").text()
        genre = document.select("p.tip:contains(Từ khóa) span a, .detail-main-info-class span a")
            .joinToString { it.text() }
        description = document.select("p.content").takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { it.wholeText().trim().substringBefore("#").trim() }
            ?: document.select("p.detail-desc")
                .joinToString("\n") { it.wholeText().trim().substringBefore("#").trim() }
        author = document.selectFirst(".subtitle:contains(Tác giả：), .detail-main-info-author:contains(Tác giả：) a")
            ?.text()?.removePrefix("Tác giả：")
        status = parseStatus(
            document.select(".block:contains(Trạng thái)").takeIf { it.isNotEmpty() }
                ?.text()
                ?: document.select(".detail-list-title-1").text(),
        )
        thumbnail_url = document.selectFirst(".banner_detail_form .cover img")?.absUrl("src")
            ?.ifEmpty {
                document.selectFirst(".detail-main-cover img")?.absUrl("data-original")
            }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        listOf("Đang Tiến Hành", "Đang Cập Nhật").any { status.contains(it, ignoreCase = true) } -> SManga.ONGOING
        listOf("Hoàn Thành", "Đã Hoàn Thành", "Đã hoàn tất").any { status.contains(it, ignoreCase = true) } -> SManga.COMPLETED
        listOf("Tạm Ngưng", "Tạm Hoãn").any { status.contains(it, ignoreCase = true) } -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun parseChapterList(document: Document): List<SChapter> = document
        .select("ul.detail-list-select li")
        .map { element ->
            SChapter.create().apply {
                val a = element.selectFirst("a")!!
                setUrlWithoutDomain(a.absUrl("href"))
                name = a.text()
                chapter_number = CHAPTER_NUMBER_REGEX.find(name)?.value?.toFloatOrNull() ?: 0f
            }
        }
        .sortedByDescending { it.chapter_number }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).use { response ->
        val document = response.asJsoup()
        document.select("img.lazy").mapIndexed { index, it ->
            val url = it.absUrl("data-original")
            val finalUrl = if (url.startsWith("https://external-content.duckduckgo.com/iu/")) {
                url.toHttpUrl().queryParameter("u")
            } else {
                url
            }
            Page(index, imageUrl = finalUrl)
        }
    }

    // ============================== Filters ===============================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Không dùng chung với tìm kiếm bằng từ khóa."),
        GenreList(),
        StatusList(),
        KeywordList(getGenreList()),
    )

    companion object {
        private val CHAPTER_NUMBER_REGEX = Regex("""(\d+(?:\.\d+)*)""")
    }
}
