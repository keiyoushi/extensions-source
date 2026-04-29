package eu.kanade.tachiyomi.extension.ru.acomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class AComics : HttpSource() {

    override val name = "AComics"

    override val baseUrl = "https://acomics.ru"

    override val lang = "ru"

    override val client = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor { chain ->
            val newReq = chain
                .request()
                .newBuilder()
                .addHeader("Cookie", "ageRestrict=17;")
                .build()

            chain.proceed(newReq)
        }.build()

    override val supportsLatest = true

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", Sort.POPULAR)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("section.serial-card").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("a > img")?.absUrl("data-real-src")
                element.selectFirst("h2 > a")!!.run {
                    setUrlWithoutDomain(attr("href") + "/about")
                    title = text()
                }
            }
        }
        val hasNextPage = document.selectFirst("a.infinite-scroll") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", Sort.LATEST)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = if (query.isNotEmpty()) {
            "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
        } else {
            val categories = mutableListOf<String>()
            val ratings = mutableListOf<String>()
            var comicType = "0"
            var publication = "0"
            var subscription = "0"
            var minPages = "2"
            var sort = "subscr_count"

            for (filter in if (filters.isEmpty()) getFilterList() else filters) {
                when (filter) {
                    is Categories -> {
                        val selected = filter
                            .state
                            .filter { it.state }
                            .map { it.id }
                            .sorted()
                            .map { it.toString() }
                        categories.addAll(selected)
                    }

                    is Ratings -> {
                        val selected = filter
                            .state
                            .filter { it.state }
                            .map { it.id }
                            .sorted()
                            .map { it.toString() }
                        ratings.addAll(selected)
                    }

                    is ComicType -> {
                        comicType = when (filter.state) {
                            1 -> "orig"
                            2 -> "trans"
                            else -> comicType
                        }
                    }

                    is Publication -> {
                        publication = when (filter.state) {
                            1 -> "no"
                            2 -> "yes"
                            else -> publication
                        }
                    }

                    is Subscription -> {
                        subscription = when (filter.state) {
                            1 -> "yes"
                            2 -> "no"
                            else -> subscription
                        }
                    }

                    is MinPages -> {
                        minPages = filter.state
                            .toIntOrNull()
                            ?.toString()
                            ?: minPages
                    }

                    is Sort -> {
                        sort = when (filter.state) {
                            0 -> "last_update"
                            1 -> "subscr_count"
                            2 -> "issue_count"
                            3 -> "serial_name"
                            else -> sort
                        }
                    }

                    else -> {}
                }
            }

            "$baseUrl/comics".toHttpUrl().newBuilder()
                .addIndexedQueryParameters("categories", categories, page == 1)
                .addIndexedQueryParameters("ratings", ratings, page == 1)
                .addQueryParameter("type", comicType)
                .addQueryParameter("updatable", publication)
                .addQueryParameter("subscribe", subscription)
                .addQueryParameter("issue_count", minPages)
                .addQueryParameter("sort", sort)
        }

        if (page > 1) {
            urlBuilder.addQueryParameter("skip", ((page - 1) * 10).toString())
        }

        return GET(urlBuilder.build(), headers)
    }

    private fun HttpUrl.Builder.addIndexedQueryParameters(
        name: String,
        values: Iterable<String?>,
        collapse: Boolean,
    ): HttpUrl.Builder = apply {
        values.forEachIndexed { i, value ->
            val key = if (collapse) "$name[]" else "$name[$i]"
            addQueryParameter(key, value)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            val article = document.selectFirst("article.common-article")!!
            with(article) {
                title = selectFirst(".page-header-with-menu h1")!!.text()
                genre = select("p.serial-about-badges a.category").joinToString { it.text() }
                author = select("p.serial-about-authors a, p:contains(Автор оригинала)").joinToString { it.ownText() }
                description = selectFirst("section.serial-about-text")?.text()
            }
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val count = doc
            .selectFirst("p:has(b:contains(Количество выпусков:))")!!
            .ownText()
            .toInt()

        val comicPath = doc.location().substringBefore("/about")

        return (count downTo 1).map {
            SChapter.create().apply {
                chapter_number = it.toFloat()
                name = it.toString()
                setUrlWithoutDomain("$comicPath/$it")
            }
        }
    }

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val imageElement = document.selectFirst("img.issue")!!
        return listOf(Page(0, imageUrl = imageElement.absUrl("src")))
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================
    override fun getFilterList() = FilterList(
        Categories(),
        Ratings(),
        Filter.Separator(),
        ComicType(),
        Publication(),
        Subscription(),
        MinPages(),
        Sort(),
    )
}
