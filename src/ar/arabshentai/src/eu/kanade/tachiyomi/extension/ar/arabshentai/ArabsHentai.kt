package eu.kanade.tachiyomi.extension.ar.arabshentai

import android.util.Base64
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ArabsHentai : KeiSource() {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/manga/page/$page/?orderby=new-manga"))

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#archive-content .wp-manga").mapNotNull { it.toPopularManga() }
        val hasNextPage = document.selectFirst(".pagination a.arrow_pag i#nextpagination") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.toPopularManga(): SManga? {
        val link = selectFirst(".data h3 a") ?: return null
        return SManga.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            title = link.text()
            thumbnail_url = selectFirst("a .poster img")?.imgAttr()
        }
    }

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/manga/page/$page/?orderby=new_chapter"))

    // =============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/page/$page/".toHttpUrl().newBuilder()
        url.addQueryParameter("s", query)
        filters.forEach { filter ->
            when (filter) {
                is GenresOpFilter -> url.addQueryParameter("op", filter.toUriPart())
                is GenresFilter -> filter.state.filter { it.state }.forEach { url.addQueryParameter("genre[]", it.uriPart) }
                is StatusFilter -> filter.state.filter { it.state }.forEach { url.addQueryParameter("status[]", it.uriPart) }
                else -> {}
            }
        }

        val document = client.get(url.build()).asJsoup()
        val mangas = document.select(".search-page .result-item article:not(:has(.tvshows))").mapNotNull { it.toSearchManga() }
        val hasNextPage = document.selectFirst(".pagination span.current + a") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.toSearchManga(): SManga? {
        val titleElement = selectFirst(".details .title") ?: return null
        val link = titleElement.selectFirst("a") ?: return null
        return SManga.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            title = titleElement.text()
            thumbnail_url = selectFirst(".image .thumbnail a img")?.imgAttr()
        }
    }

    // =========================== Manga Details ============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val content = document.selectFirst(".content") ?: throw Exception("Failed to parse manga details")
        val updatedManga = manga.apply {
            content.selectFirst(".sheader .data h1")?.let { title = it.text() }
            thumbnail_url = content.selectFirst(".sheader .poster img")?.imgAttr()
            val genres = mutableListOf<String>()
            content.selectFirst("#manga-info")?.let { info ->
                description = "؜" + info.select(".wp-content p").text() + "\n" + "أسماء أُخرى: " + info.select("div b:contains(أسماء أُخرى) + span").text()
                status = info.select("div b:contains(حالة المانجا) + span").text().parseStatus()
                author = info.select("div b:contains(الكاتب) + span a").text()
                artist = info.select("div b:contains(الرسام) + span a").text()
                genres += info.select("div b:contains(نوع العمل) + span a").text()
            }
            genres += content.select(".data .sgeneros a").map { it.text() }
            genre = genres.joinToString()
        }

        val chapterList = document.select("#chapter-list:not(.oneshot-reader) a[href*='/manga/'], .oneshot-reader .image-item a[href$='style=paged']")
            .map { it.toChapter() }
            .distinctBy { it.url }

        return SMangaUpdate(updatedManga, chapterList)
    }

    private fun String?.parseStatus() = when {
        this == null -> SManga.UNKNOWN
        contains("مستمر", ignoreCase = true) -> SManga.ONGOING
        contains("مكتمل", ignoreCase = true) -> SManga.COMPLETED
        contains("متوقف", ignoreCase = true) -> SManga.ON_HIATUS
        contains("ملغية", ignoreCase = true) -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    private fun Element.toChapter(): SChapter = SChapter.create().apply {
        val url = absUrl("href")
        if (url.contains("style=paged")) {
            setUrlWithoutDomain(url.substringBeforeLast("?"))
            name = "ونشوت"
            date_upload = 0L
        } else {
            name = select(".chapternum").text()
            date_upload = dateFormat.tryParseDate(select(".chapterdate").text())
            setUrlWithoutDomain(url)
        }
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val script = client.get(getChapterUrl(chapter)).asJsoup()
            .selectFirst("script:containsData(const images = [)")!!.data()
        return script.substringAfter("const images = ").substringBefore("];").plus("]").parseAs<List<ImageDto>>()
            .mapIndexed { index, image ->
                Page(index, imageUrl = String(Base64.decode(image.url, Base64.DEFAULT)))
            }
    }

    private fun Element.imgAttr(): String? = when {
        hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("bv-data-src") -> attr("bv-data-src")
        else -> attr("abs:src")
    }

    // =============================== Filters ==============================
    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val items = client.get("$baseUrl/%d8%aa%d8%b5%d9%86%d9%8a%d9%81%d8%a7%d8%aa").asJsoup()
            .select("#archive-content ul.genre-list li.item-genre .genre-data a")
        return items.map {
            val value = it.ownText()
            Pair(value, value)
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>()
        data?.parseAs<List<Pair<String, String>>>()?.also {
            filters.add(GenresFilter(it))
        }
        filters.add(GenresOpFilter())
        filters.add(StatusFilter())
        return FilterList(filters)
    }
}

@Serializable
class ImageDto(val url: String)
