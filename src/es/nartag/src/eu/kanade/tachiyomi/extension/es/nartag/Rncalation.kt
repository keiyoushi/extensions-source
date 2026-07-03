package eu.kanade.tachiyomi.extension.es.nartag

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Rncalation : HttpSource() {

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(2, 1.seconds)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/library?sort=views&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".lib-grid a.comic-card").mapNotNull { element ->
            val type = element.selectFirst("span.absolute.top-2.left-2")?.text()
            if (type != null && type.contains("Novel", ignoreCase = true)) {
                return@mapNotNull null
            }
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst("p.leading-snug")!!.text()
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        val hasNextPage = document.selectFirst("a.lib-page-btn--nav:last-child") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/library?sort=latest&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/library".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotEmpty()) {
                addQueryParameter("q", query)
            }
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        addQueryParameter("sort", sortOptions[filter.state].value)
                    }
                    is TypeFilter -> {
                        if (filter.state > 0) {
                            addQueryParameter("type", filter.values[filter.state])
                        }
                    }
                    is StatusFilter -> {
                        if (filter.state > 0) {
                            addQueryParameter("status", filter.values[filter.state])
                        }
                    }
                    is GenreFilter -> {
                        if (filter.state > 0) {
                            addQueryParameter("genre", filter.values[filter.state])
                        }
                    }
                    else -> {}
                }
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            description = document.selectFirst("div.comic-page-wrap p[class*=text-][^data-astro-cid]")?.text() ?: ""

            val badges = document.select("span.inline-flex.items-center.rounded").map { it.text().lowercase() }
            status = when {
                badges.any { it.contains("emisión") || it.contains("curso") || it.contains("ongoing") } -> SManga.ONGOING
                badges.any { it.contains("completado") || it.contains("completed") } -> SManga.COMPLETED
                badges.any { it.contains("pausa") || it.contains("hiatus") } -> SManga.ON_HIATUS
                badges.any { it.contains("cancelado") || it.contains("cancelled") } -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }

            genre = document.select("span.inline-flex.items-center.rounded")
                .filter { it.text().lowercase() !in listOf("emisión", "completado", "pausa", "cancelado") }
                .joinToString(", ") { it.text() }

            val groupName = document.selectFirst("a[href^='/groups/']")?.text()
            if (!groupName.isNullOrEmpty()) {
                author = groupName
                artist = groupName
            }

            document.select(".flex.items-baseline.justify-between.gap-2").forEach { row ->
                val label = row.selectFirst("span.text-\\[var\\(--color-text3\\)\\]")?.text()
                val value = row.selectFirst("span.text-\\[var\\(--color-text2\\)\\]")?.text()
                if (label == "Autor") author = value
                if (label == "Arte") artist = value
            }
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val slug = manga.url.removeSuffix("/").substringAfterLast("/")
        val allChapters = mutableListOf<SChapter>()
        var page = 1
        do {
            val response = client.newCall(chapterListRequest(slug, page)).execute()
            val chapters = chapterListParse(response)
            if (chapters.isEmpty()) break
            allChapters.addAll(chapters)

            val currentPage = response.header("x-page")?.toIntOrNull() ?: break
            val totalPages = response.header("x-pages")?.toIntOrNull() ?: break

            page++
        } while (currentPage < totalPages)

        return@fromCallable allChapters.toList()
    }

    private fun chapterListRequest(slug: String, page: Int) = GET("$baseUrl/comics/$slug/chapters?page=$page", headers)

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().select("a[data-chapter-id]").mapIndexed { num, it ->
        SChapter.create().apply {
            setUrlWithoutDomain(it.attr("href"))
            chapter_number = it.attr("data-chapter-num").toFloatOrNull() ?: num.toFloat()
            name = it.attr("data-chapter-label").trim().ifEmpty { "Capítulo ${chapter_number.toInt()}" }
            date_upload = it.selectFirst(".text-\\[0\\.65rem\\]")?.let { parseDate(it.text()) } ?: 0L
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img.page-img, .page-wrap img").mapIndexed { index, element ->
            val imageUrl = element.attr("abs:data-src").ifEmpty { element.attr("abs:src") }
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        GenreFilter(genresList),
    )
}
