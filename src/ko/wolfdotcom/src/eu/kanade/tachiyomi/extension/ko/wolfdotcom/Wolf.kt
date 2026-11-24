package eu.kanade.tachiyomi.extension.ko.wolfdotcom

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Wolf : KeiSource() {

    private val isWebtoon get() = name.endsWith("웹툰")
    private val isComic get() = name.endsWith("만화책")
    private val isPhoto get() = name.endsWith("포토툰")

    private val browsePath get() = when {
        isComic -> "cm"
        isPhoto -> "pt"
        else -> "ing" // Webtoon
    }

    private val entryPath get() = when {
        isComic -> "cl"
        else -> "list"
    }

    private val readerPath get() = when {
        isComic -> "cv"
        else -> "view"
    }

    // Old domains serve a notice linking the current one
    override suspend fun fetchBaseUrls(client: OkHttpClient): List<String> = listOfNotNull(
        client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
            .selectFirst(".card a.main-btn")
            ?.absUrl("href"),
    )

    private val sortOptions get() = if (isComic) {
        listOf("최신순" to "n", "인기순" to "f")
    } else {
        listOf("최신순" to "n", "신작순" to "r", "인기순" to "f")
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(sortOptions, "f")))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(sortOptions, "n")))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            if (query.length < 2) {
                throw Exception("두 글자 이상 입력 해주세요.")
            }
            "$baseUrl/sh".toHttpUrl().newBuilder()
                .addEncodedQueryParameter("q", URLEncoder.encode(query.trim(), "EUC-KR"))
        } else {
            val path = if (isWebtoon && filters.firstInstanceOrNull<StatusFilter>()?.state == 1) "end" else browsePath
            "$baseUrl/$path".toHttpUrl().newBuilder().apply {
                filters.filterIsInstance<UrlPartFilter>().forEach { filter ->
                    filter.addToUrl(this)
                }
            }
        }
            .addQueryParameter("pg", page.toString())
            .build()

        return parseMangaList(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("a.t-card[href*=/$entryPath?]").map { el ->
            SManga.create().apply {
                url = el.absUrl("href").toHttpUrl().queryParameter("toon")!!
                title = el.selectFirst(".t-title")!!.text()
                thumbnail_url = el.selectFirst(".t-img img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, document.hasNextPage())
    }

    private fun Document.hasNextPage() = selectFirst(".pagi .pg-btn.on + a.pg-btn") != null

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(entryPath)
        .addQueryParameter("toon", manga.url)
        .toString()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        var document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()

        val updatedManga = manga.apply {
            title = document.selectFirst("h1.w-title")!!.text()
            thumbnail_url = document.selectFirst(".thumb-wrap img")?.absUrl("src")
            description = MarkdownDescription.Builder()
                .description(document.selectFirst(".summary"))
                .build()
            genre = document.select(".genre-tags a.gtag").joinToString { it.text().removePrefix("#") }
        }

        if (!fetchChapters) return SMangaUpdate(updatedManga, chapters)

        val updatedChapters = mutableListOf<SChapter>()
        var page = 1
        while (true) {
            document.select("a.ep-item").mapTo(updatedChapters) { el ->
                val chapUrl = el.absUrl("href").toHttpUrl()
                SChapter.create().apply {
                    url = ChapterUrl(
                        chapUrl.queryParameter("toon")!!,
                        chapUrl.queryParameter("num")!!,
                    ).toJsonString()
                    name = el.selectFirst(".ep-title")!!.text()
                    date_upload = dateFormat.tryParseDate(el.selectFirst(".ep-date")?.text())
                }
            }
            if (!document.hasNextPage()) break

            page++
            val url = getMangaUrl(manga).toHttpUrl().newBuilder()
                .addQueryParameter("s", "n")
                .addQueryParameter("pg", page.toString())
                .build()
            document = client.get(url, Fetch.DOCUMENT).asJsoup()
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    @Serializable
    class ChapterUrl(
        val toon: String,
        val num: String,
    )

    override fun getChapterUrl(chapter: SChapter): String {
        val chapUrl = chapter.url.parseAs<ChapterUrl>()

        return baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(readerPath)
            .addQueryParameter("toon", chapUrl.toon)
            .addQueryParameter("num", chapUrl.num)
            .toString()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()

        return document.select("#vimg-area img[data-src]").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.absUrl("data-src"))
        }
    }

    override val supportsFilterFetching get() = !isPhoto

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/$browsePath", Fetch.DOCUMENT).asJsoup()

        return document.select(".f-row").mapNotNull { row ->
            val links = row.select("a.ftag").map { it.ownText() to it.absUrl("href").toHttpUrl() }
            val param = FILTER_PARAMS.firstOrNull { param ->
                links.any { !it.second.queryParameter(param).isNullOrEmpty() }
            } ?: return@mapNotNull null

            FilterRow(
                param = param,
                options = links.map { (name, url) -> FilterOption(name, url.queryParameter(param).orEmpty()) },
            )
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        if (isPhoto) return FilterList()

        val filters: MutableList<Filter<*>> = mutableListOf(
            Filter.Header("검색어 입력 시 필터는 무시됩니다"),
            SortFilter(sortOptions),
        )

        if (isWebtoon) {
            filters.add(StatusFilter())
        }

        data?.parseAs<List<FilterRow>>()?.forEach {
            filters.add(RowFilter(it))
        }

        return FilterList(filters)
    }
}

private val FILTER_PARAMS = listOf("t1", "t2", "t3")

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
