package eu.kanade.tachiyomi.extension.ko.toon11

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Toon11 : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/bbs/board.php?bo_table=toon_c&is_over=0").asJsoup()
        val mangas = document.select("li[data-id]").map { element ->
            SManga.create().apply {
                title = element.selectFirst(".homelist-title")!!.text()
                url = mangaUrl(title, element.attr("data-id"))
                thumbnail_url = element.selectFirst(".homelist-thumb")?.absUrl("data-mobile-image")
            }
        }
        val hasNextPage = document.selectFirst(".pg_end") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/bbs/board.php?bo_table=toon_c&sord=&type=upd&page=$page").asJsoup()
        val mangas = document.select("li[data-id]").map { element ->
            SManga.create().apply {
                title = element.selectFirst(".homelist-title")!!.text()
                url = mangaUrl(title, element.attr("data-id"))
                element.selectFirst(".homelist-thumb")?.also {
                    thumbnail_url = "https:" + it.attr("style").substringAfter("url('").substringBefore("')")
                }
            }
        }
        val hasNextPage = document.selectFirst(".pg_end") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val searchUrl = if (query.isNotBlank()) {
            "$baseUrl/bbs/search_stx.php".toHttpUrl().newBuilder().apply {
                addQueryParameter("stx", query)
            }.build()
        } else {
            val sortFilter = filters.firstInstanceOrNull<SortFilter>()
            val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
            val genreFilter = filters.firstInstanceOrNull<GenreFilter>()

            val urlString = sortFilter?.selected ?: sortList[0].value
            val isOver = statusFilter?.selected ?: ""
            val genre = genreFilter?.selected ?: ""

            (baseUrl + urlString).toHttpUrl().newBuilder().apply {
                addQueryParameter("is_over", isOver)
                if (page > 1) addQueryParameter("page", page.toString())
                if (genre.isNotEmpty()) addQueryParameter("sca", genre)
            }.build()
        }

        val document = client.get(searchUrl).asJsoup()
        val mangas = document.select("li[data-id]").map { element ->
            SManga.create().apply {
                title = element.selectFirst(".homelist-title")!!.text()
                url = mangaUrl(title, element.attr("data-id"))
                element.selectFirst(".homelist-thumb")?.also {
                    thumbnail_url = "https:" + it.attr("style").substringAfter("url('").substringBefore("')")
                }
            }
        }
        val hasNextPage = document.selectFirst(".pg_end") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Only "is" identifies a manga, so every listing builds the same URL the popular listing links to
    private fun mangaUrl(title: String, id: String) = "/bbs/board.php?bo_table=toons&stx=$title&is=$id"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        if (url.queryParameter("bo_table") != "toons") return null
        val id = url.queryParameter("is") ?: return null

        val document = client.get("$baseUrl/bbs/board.php?bo_table=toons&is=$id").asJsoup()
        return parseMangaDetails(document).apply {
            this.url = mangaUrl(title, id)
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(
            parseMangaDetails(document).apply { url = manga.url },
            if (fetchChapters) parseChapterList(document) else chapters,
        )
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h2.title")!!.text()
        thumbnail_url = document.selectFirst("img.banner")?.absUrl("src")
        document.selectFirst("span:contains(분류) + span")?.also { status = parseStatus(it.text()) }
        document.selectFirst("span:contains(작가) + span")?.also { author = it.text() }
        document.selectFirst("span:contains(소개) + span")?.also { description = it.text() }
        document.selectFirst("span:contains(장르) + span")?.also { genre = it.text().split(",").joinToString { s -> s.trim() } }
    }

    private fun parseStatus(element: String): Int = when {
        "완결" in element -> SManga.COMPLETED
        "주간" in element || "월간" in element || "연재" in element || "격주" in element -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    private suspend fun parseChapterList(document: Document): List<SChapter> {
        val chapters = document.select("#comic-episode-list > li").mapTo(ArrayList(), ::parseChapter)

        if (document.selectFirst("span.pg") == null) {
            return chapters
        }

        var nextUrl = document.selectFirst(".pg_current ~ .pg_page")?.absUrl("href")

        while (!nextUrl.isNullOrBlank()) {
            val newpage = client.get(nextUrl).asJsoup()
            newpage.select("#comic-episode-list > li").mapTo(chapters, ::parseChapter)
            nextUrl = newpage.selectFirst(".pg_current ~ .pg_page")?.absUrl("href")
        }

        return chapters
    }

    private fun parseChapter(element: Element): SChapter {
        val urlEl = element.selectFirst("button")
        val dateEl = element.selectFirst(".free-date")

        return SChapter.create().apply {
            urlEl?.also {
                url = it.attr("onclick").substringAfter("location.href='.").substringBefore("'")
                name = it.selectFirst(".episode-title")!!.text()
            }
            dateEl?.also { date_upload = dateFormat.tryParseDate(it.ownText()) }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + "/bbs" + chapter.url

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val script = document.selectFirst("script:containsData(img_list)")?.data() ?: return emptyList()
        val imgList = extractList(imgListRegex, script)
        val fallbackList = extractList(imgList2Regex, script)

        return imgList.mapIndexed { i, img ->
            val fallback = fallbackList.getOrNull(i)?.let { "#https:$it" }.orEmpty()
            Page(i, imageUrl = "https:$img$fallback")
        }
    }

    private fun extractList(regex: Regex, jsString: String): List<String> {
        val listString = regex.find(jsString)?.groupValues?.get(1) ?: return emptyList()
        return listString.parseAs<List<String>>()
    }

    // The site's viewer loads img_list and falls back to the img_list_2 mirror when an image fails
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor { chain ->
        val request = chain.request()
        val fallback = request.url.fragment?.takeIf { it.startsWith("https://") }
            ?: return@addInterceptor chain.proceed(request)

        val response = try {
            chain.proceed(request)
        } catch (_: IOException) {
            null
        }
        if (response?.isSuccessful == true) return@addInterceptor response

        response?.close()
        chain.proceed(request.newBuilder().url(fallback).build())
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Note: can't combine search query with filters, status filter only has effect in 인기만화"),
        Filter.Separator(),
        SortFilter(sortList, 0),
        StatusFilter(statusList, 0),
        GenreFilter(genreList, 0),
    )

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("yy.MM.dd", Locale.ENGLISH)
        private val imgListRegex = """img_list\s*=\s*(\[.*?])""".toRegex(RegexOption.DOT_MATCHES_ALL)
        private val imgList2Regex = """img_list_2\s*=\s*(\[.*?])""".toRegex(RegexOption.DOT_MATCHES_ALL)
    }
}
