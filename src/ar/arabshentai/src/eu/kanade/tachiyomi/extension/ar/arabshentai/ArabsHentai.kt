package eu.kanade.tachiyomi.extension.ar.arabshentai

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ArabsHentai : KeiSource() {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get(mangaListUrl(page, "new-manga"), Fetch.DOCUMENT))

    private fun mangaListUrl(page: Int, orderBy: String): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments(if (page > 1) "manga/page/$page/" else "manga/")
        .addQueryParameter("orderby", orderBy)
        .build()

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

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get(mangaListUrl(page, "new_chapter"), Fetch.DOCUMENT))

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

        val document = client.get(url.build(), Fetch.DOCUMENT).asJsoup()
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

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()

        val content = document.selectFirst(".content") ?: throw Exception("تعذر قراءة تفاصيل المانجا")
        val info = content.selectFirst("#manga-info")
        val updatedManga = manga.apply {
            content.selectFirst(".sheader .data h1")?.let { title = it.text() }
            thumbnail_url = content.selectFirst(".sheader .poster img")?.imgAttr()
            if (info != null) {
                description = MarkdownDescription.Builder()
                    .description(info.selectFirst(".wp-content"))
                    .altTitles(info.field("أسماء أُخرى")?.text()?.split(",").orEmpty(), "أسماء أُخرى")
                    .build()
                status = info.field("حالة المانجا")?.text().parseStatus()
                author = info.names("الكاتب")
                artist = info.names("الرسام")
            }
            genre = (info?.field("نوع العمل")?.select("a").orEmpty() + content.select(".data .sgeneros a"))
                .map { it.text() }
                .distinct()
                .joinToString()
                .ifEmpty { null }
        }

        val translators = info?.names("اسم المترجم")
        val chapterList = document.select("#chapter-list:not(.oneshot-reader) a[href*='/manga/'], .oneshot-reader .image-item a[href$='style=paged']")
            .map { it.toChapter().apply { scanlator = translators } }
            .distinctBy { it.url }

        return SMangaUpdate(updatedManga, chapterList)
    }

    private fun Element.field(label: String): Element? = selectFirst(".custom_fields b:containsOwn($label) + span")

    private fun Element.names(label: String): String? = field(label)?.select("a")?.eachText()?.joinToString()?.ifEmpty { null }

    private fun String?.parseStatus() = when {
        this == null -> SManga.UNKNOWN
        contains("مستمر") -> SManga.ONGOING
        contains("مكتمل") -> SManga.COMPLETED
        contains("متوقف") -> SManga.ON_HIATUS
        contains("ملغية") -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun Element.toChapter(): SChapter = SChapter.create().apply {
        val url = absUrl("href")
        if (url.contains("style=paged")) {
            setUrlWithoutDomain(url.substringBeforeLast("?"))
            name = "ونشوت"
        } else {
            name = selectFirst(".chapternum")!!.text()
            date_upload = dateFormat.tryParseDate(selectFirst(".chapterdate")?.text())
            setUrlWithoutDomain(url)
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}/"

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(".chapter_image img.wp-manga-chapter-img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    private fun Element.imgAttr(): String? = when {
        hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("bv-data-src") -> attr("abs:bv-data-src")
        else -> attr("abs:src")
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val items = client.get("$baseUrl/%d8%aa%d8%b5%d9%86%d9%8a%d9%81%d8%a7%d8%aa/", Fetch.DOCUMENT).asJsoup()
            .select("#archive-content ul.genre-list li.item-genre .genre-data a")
        return items.map {
            val value = it.ownText()
            value to value
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
