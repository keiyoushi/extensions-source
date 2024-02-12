package eu.kanade.tachiyomi.extension.all.mangareaderto

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

open class MangaReader(
    override val lang: String,
) : MangaReader(
    "MangaReader",
    "https://mangareader.to",
    lang,
    containsVolumes = true,
    chapterType = "chap",
    volumeType = "vol",
    sortFilterValues = arrayOf(
        Pair("Default", "default"),
        Pair("Latest Updated", "latest-updated"),
        Pair("Score", "score"),
        Pair("Name A-Z", "name-az"),
        Pair("Release Date", "release-date"),
        Pair("Most Viewed", "most-viewed"),
    ),
) {

    override val client = network.client.newBuilder()
        .addInterceptor(ImageInterceptor)
        .build()

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val req = super.searchMangaRequest(page, query, filters)
        return req.newBuilder().apply {
            if (query.isBlank()) {
                url(
                    req.url.newBuilder()
                        .addQueryParameter("language", lang)
                        .build(),
                )
            }
        }.build()
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(mangaUrl: String, type: String): Request {
        val id = mangaUrl.substringAfterLast('-')
        return GET("$baseUrl/ajax/manga/reading-list/$id?readingBy=$type", headers)
    }

    override fun parseChapterElements(response: Response, isVolume: Boolean): List<Element> {
        val container = response.parseHtmlProperty().run {
            val type = if (isVolume) "volumes" else "chapters"
            selectFirst("#$lang-$type") ?: return emptyList()
        }
        return container.children()
    }

    override fun chapterFromElement(element: Element, isVolume: Boolean): SChapter =
        chapterFromElement(element)

    // =============================== Pages ================================

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        val typeAndId = chapter.url.substringAfterLast('#', "").ifEmpty {
            val document = client.newCall(pageListRequest(chapter)).execute().asJsoup()
            val wrapper = document.selectFirst("#wrapper")!!
            wrapper.attr("data-reading-by") + '/' + wrapper.attr("data-reading-id")
        }
        val ajaxUrl = "$baseUrl/ajax/image/list/$typeAndId?quality=${preferences.quality}"
        client.newCall(GET(ajaxUrl, headers)).execute().let(::pageListParse)
    }

    override fun pageListParse(response: Response): List<Page> {
        val pageDocument = response.parseHtmlProperty()

        return pageDocument.getElementsByClass("iv-card").mapIndexed { index, img ->
            val url = img.attr("data-url")
            val imageUrl = if (img.hasClass("shuffled")) "$url#${ImageInterceptor.SCRAMBLED}" else url
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================= Utilities ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        getPreferences(screen.context).forEach(screen::addPreference)
        super.setupPreferenceScreen(screen)
    }

    // =============================== Filters ==============================

    override fun getFilterList() = FilterList(
        Note,
        TypeFilter(),
        StatusFilter(),
        RatingFilter(),
        ScoreFilter(),
        StartDateFilter(),
        EndDateFilter(),
        getSortFilter(),
        GenreFilter(),
    )
}
