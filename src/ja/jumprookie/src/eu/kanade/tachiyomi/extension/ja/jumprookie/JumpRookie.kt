package eu.kanade.tachiyomi.extension.ja.jumprookie

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element

@Source
abstract class JumpRookie : KeiSource() {
    private var nextPageKey: String? = null

    private val desktopHeaders get() = headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36")
        .build()

    override suspend fun getPopularManga(page: Int): MangasPage = getSeriesList(page, null)

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/categories/general/recent".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()

        val document = client.get(url, desktopHeaders).asJsoup()
        val mangas = document.select("section.series-contents").map { it.toSManga() }
        val hasNextPage = document.selectFirst(".button-next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("query", query)
                .build()

            val document = client.get(url).asJsoup()
            val mangas = document.select("#search-series section.series-contents").map { it.toSManga() }
            return MangasPage(mangas, false)
        }

        val genre = filters.firstInstanceOrNull<GenreFilter>()?.value
        return getSeriesList(page, genre)
    }

    private suspend fun getSeriesList(page: Int, category: String?): MangasPage {
        if (page == 1) nextPageKey = null
        val url = "$baseUrl/api/media/series_list".toHttpUrl().newBuilder().apply {
            addQueryParameter("type", "popular")
            if (!category.isNullOrEmpty()) addQueryParameter("category", category)
            nextPageKey?.let { addQueryParameter("key", it) }
        }.build()

        val response = client.get(url)
        nextPageKey = response.header("Tky-Link-Rel-Next")
            ?.let { baseUrl.toHttpUrl().resolve(it) }
            ?.queryParameter("key")

        val mangas = response.asJsoup().select("section.series-contents").map { it.toSManga() }
        val hasNextPage = nextPageKey != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.toSManga(): SManga = SManga.create().apply {
        setUrlWithoutDomain(selectFirst("a")!!.absUrl("href"))
        title = selectFirst(".series-title")!!.text()
        thumbnail_url = selectFirst(".cover-image")?.absUrl("src")
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val details = SManga.create().apply {
            title = document.selectFirst(".series-title")!!.text()
            author = document.selectFirst(".user-name")?.text()
            description = document.selectFirst(".series-description")?.text()
            genre = document.select(".series-category").joinToString { it.text() }
            thumbnail_url = document.selectFirst(".cover-image")?.absUrl("src")
        }

        val chapterList = document.select("#episode-list > li").map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.selectFirst("a.episode-content")!!.absUrl("href"))
                name = it.selectFirst(".episode-title")!!.text()
            }
        }.reversed()

        return SMangaUpdate(
            details,
            chapterList,
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".js-page-image").mapIndexed { i, element ->
            Page(i, imageUrl = element.absUrl("src"))
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        GenreFilter(),
    )
}
