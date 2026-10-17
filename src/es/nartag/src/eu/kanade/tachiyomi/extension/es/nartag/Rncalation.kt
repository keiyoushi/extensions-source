package eu.kanade.tachiyomi.extension.es.nartag

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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Rncalation : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2, 1.seconds).addInterceptor { chain ->
        val response = chain.proceed(chain.request())
        // Without the mv_ck cookie the site serves a JS page that sets it and reloads; the cookie is saved by now, so retry once
        if (response.header("Set-Cookie")?.startsWith("mv_ck=") == true && response.peekBody(4096).string().contains("mv-verifying")) {
            response.close()
            chain.proceed(chain.request())
        } else {
            response
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/library?sort=views&page=$page").asJsoup())

    private fun parseMangaList(document: Document): MangasPage {
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

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/library?sort=updated&page=$page").asJsoup())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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
        return parseMangaList(client.get(url).asJsoup())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) fetchMangaDetails(manga) else manga }
        val chapterList = async { if (fetchChapters) fetchChapterList(manga) else chapters }

        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return manga.apply {
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

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val slug = manga.url.removeSuffix("/").substringAfterLast("/")
        val allChapters = mutableListOf<SChapter>()
        var page = 1
        do {
            val response = client.get("$baseUrl/comics/$slug/chapters?page=$page")
            val currentPage = response.header("x-page")?.toIntOrNull()
            val totalPages = response.header("x-pages")?.toIntOrNull()

            val chapters = response.asJsoup().select("a[data-chapter-id]").mapIndexed { num, it ->
                SChapter.create().apply {
                    setUrlWithoutDomain(it.attr("href"))
                    chapter_number = it.attr("data-chapter-num").toFloatOrNull() ?: num.toFloat()
                    name = it.attr("data-chapter-label").trim().ifEmpty { "Capítulo ${chapter_number.toInt()}" }
                    date_upload = it.selectFirst(".text-\\[0\\.65rem\\]")?.let { parseDate(it.text()) } ?: 0L
                }
            }
            if (chapters.isEmpty()) break
            allChapters.addAll(chapters)

            if (currentPage == null || totalPages == null) break

            page++
        } while (currentPage < totalPages)

        return allChapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("img.page-img, .page-wrap img").mapIndexed { index, element ->
            val imageUrl = element.attr("abs:data-src").ifEmpty { element.attr("abs:src") }
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        GenreFilter(genresList),
    )
}
