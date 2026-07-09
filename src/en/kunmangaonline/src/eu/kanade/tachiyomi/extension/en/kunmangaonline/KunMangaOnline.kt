package eu.kanade.tachiyomi.extension.en.kunmangaonline

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class KunMangaOnline : Madara() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val client = network.client.newBuilder()
        .rateLimit(2) { it.host == baseUrlHost }
        .build()

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        val url = if (page > 1) {
            "$baseUrl/page/$page/?orderby=views&post_type=wp-manga"
        } else {
            "$baseUrl/?orderby=views&post_type=wp-manga"
        }
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaCards(response)

    // Recent

    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("action", "madara_load_more")
            addQueryParameter("page", (page - 1).toString())
            addQueryParameter("template", "madara-core/content/content-archive")
            addQueryParameter("vars[orderby]", "meta_value_num")
            addQueryParameter("vars[paged]", page.toString())
            addQueryParameter("vars[timerange]", "")
            addQueryParameter("vars[posts_per_page]", POSTS_PER_PAGE.toString())
            addQueryParameter("vars[tax_query][relation]", "OR")
            addQueryParameter("vars[meta_query][0][relation]", "AND")
            addQueryParameter("vars[meta_query][relation]", "AND")
            addQueryParameter("vars[post_type]", "wp-manga")
            addQueryParameter("vars[post_status]", "publish")
            addQueryParameter("vars[meta_key]", "_latest_update")
            addQueryParameter("vars[order]", "desc")
            addQueryParameter("vars[sidebar]", "right")
            addQueryParameter("vars[manga_archives_item_layout]", "big_thumbnail")
            addQueryParameter("_", System.currentTimeMillis().toString())
        }.build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaCards(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = (if (page > 1) "$baseUrl/page/$page/" else baseUrl).toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) addQueryParameter("s", query)
            addQueryParameter("post_type", "wp-manga")

            filters.firstInstanceOrNull<AuthorFilter>()?.state
                ?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("author", it) }
            filters.firstInstanceOrNull<ArtistFilter>()?.state
                ?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("artist", it) }
            filters.firstInstanceOrNull<YearFilter>()?.state
                ?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("release", it) }

            filters.firstInstanceOrNull<OperatorFilter>()
                ?.let { addQueryParameter("op", it.selectedValue()) }

            filters.firstInstanceOrNull<AdultFilter>()?.let {
                val adult = it.selectedValue()
                if (adult.isNotEmpty()) addQueryParameter("adult", adult)
            }
            filters.firstInstanceOrNull<KMoOrderByFilter>()?.let {
                val order = it.selectedValue()
                if (order.isNotEmpty()) addQueryParameter("orderby", order)
            }

            filters.firstInstanceOrNull<GenreListFilter>()?.state
                ?.filter { it.state }?.forEach { addQueryParameter("genre[]", it.value) }
            filters.firstInstanceOrNull<StatusListFilter>()?.state
                ?.filter { it.state }?.forEach { addQueryParameter("status[]", it.value) }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaCards(response)

    // Shared parsing

    private fun parseMangaCards(response: Response): MangasPage {
        val document = response.asJsoup()
        val baseHost = baseUrlHost

        val mangas = document.select(".c-tabs-item__content, .page-item-detail").mapNotNull { element ->
            val titleEl = element.selectFirst(".post-title a, h3.h4 a") ?: return@mapNotNull null

            SManga.create().apply {
                title = titleEl.ownText()
                setUrlWithoutDomain(titleEl.absUrl("href"))

                thumbnail_url = element.selectFirst("img")?.let { img ->
                    listOf("data-backup", "src", "data-src", "data-lazy-src", "data-aload")
                        .map { img.absUrl(it) }
                        .firstOrNull { it.startsWith("http") && !it.contains("$baseHost/thumb") }
                }
            }
        }

        val isAjax = response.request.url.queryParameter("action") == "madara_load_more"

        val hasNextPage = if (isAjax) {
            mangas.size >= POSTS_PER_PAGE
        } else {
            document.selectFirst("a[aria-label=Next], .nav-previous, .next.page-numbers, .pagination-next, .wp-pagenavi .next, a[rel=next], .next") != null
        }

        return MangasPage(mangas, hasNextPage)
    }

    // Chapters

    override fun fetchChapterList(manga: SManga): rx.Observable<List<SChapter>> = rx.Observable.fromCallable {
        val allChapters = mutableListOf<SChapter>()

        val slug = "$baseUrl${manga.url}".toHttpUrl().pathSegments.getOrNull(1) ?: return@fromCallable emptyList()

        var currentPage = 1
        var lastPage = 1

        do {
            val apiUrl = "$baseUrl/api/comics/$slug/chapters?page=$currentPage&per_page=$CHAPTERS_PER_PAGE&order=desc"
            val response = client.newCall(GET(apiUrl, apiHeaders)).execute()
            val data = response.parseAs<ChapterListResponse>().data

            lastPage = data.lastPage
            data.chapters.forEach { chapter ->
                allChapters.add(chapter.toSChapter(slug) { apiDateFormat.tryParse(it?.substringBefore(".")) })
            }

            currentPage++
        } while (currentPage <= lastPage)

        allChapters
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, imageHeaders)

    // Filters

    override fun getFilterList(): FilterList = FilterList(
        AuthorFilter(),
        ArtistFilter(),
        YearFilter(),

        OperatorFilter(),

        AdultFilter(),
        KMoOrderByFilter(),

        Filter.Separator(),

        GenreListFilter(getGenreList()),
        StatusListFilter(getStatusList()),
    )

    // Private

    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val apiHeaders by lazy {
        headers.newBuilder()
            .add("Accept", "application/json")
            .build()
    }

    private val imageHeaders = headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)
        .build()

    // Companion

    companion object {
        private const val POSTS_PER_PAGE = 20
        private const val CHAPTERS_PER_PAGE = 50
    }
}
