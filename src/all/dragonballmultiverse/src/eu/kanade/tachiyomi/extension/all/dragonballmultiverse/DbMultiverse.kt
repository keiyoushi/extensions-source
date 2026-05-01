package eu.kanade.tachiyomi.extension.all.dragonballmultiverse

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import rx.Observable

abstract class DbMultiverse(override val lang: String, private val internalLang: String) : HttpSource() {

    override val name =
        if (internalLang.endsWith("_PA")) {
            "Dragon Ball Multiverse Parody"
        } else {
            "Dragon Ball Multiverse"
        }

    override val baseUrl = "https://www.dragonball-multiverse.com"

    override val supportsLatest = false

    private fun createManga(type: String) = SManga.create().apply {
        title = when (type) {
            "comic" -> "DB Multiverse"
            "namekseijin" -> "Namekseijin Densetsu"
            "strip" -> "Minicomic"
            else -> name
        }
        status = SManga.ONGOING
        url = "/$internalLang/chapters.html?comic=$type"
        description = "Dragon Ball Multiverse (DBM) is a free online comic, made by a whole team of fans. It's our personal sequel to DBZ."
        thumbnail_url = "$baseUrl/imgs/read/$type.jpg"
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        // site hosts three titles that can be read by the app
        return listOf("page", "strip", "namekseijin")
            .map { createManga(it) }
            .let { Observable.just(MangasPage(it, hasNextPage = false)) }
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = manga.apply {
        initialized = true
    }.let { Observable.just(it) }

    protected open val chapterListSelector: String = ".cadrelect.chapter p a[href*=-]"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector).map {
            SChapter.create().apply {
                val href = it.attr("href")
                setUrlWithoutDomain("/$internalLang/$href")
                name = "Page " + it.text()
            }
        }.reversed()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("#balloonsimg")
            .let { e ->
                val imageUrl = when {
                    e.hasAttr("src") -> e.attr("abs:src")
                    e.selectFirst("img") != null -> e.selectFirst("img")!!.attr("abs:src")
                    else -> baseUrl + e.attr("style").substringAfter("(").substringBefore(")")
                }

                listOf(Page(1, imageUrl = imageUrl))
            }
    }

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
