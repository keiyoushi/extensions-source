package eu.kanade.tachiyomi.extension.ja.momonga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SManga.Companion.COMPLETED
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MomonGA : KeiSource() {
    override val supportsLatest = false

    // Chapters

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日H時", Locale.ENGLISH)

    // LatestUpdate (Not supported)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Details

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(baseUrl + manga.url)
        val chapterUrl = response.request.url.encodedPath
        val document = response.asJsoup()

        val details = SManga.create().apply {
            document.select("#post-tag > div.post-tag-table").forEach { div ->
                when (div.selectFirst("div.post-tag-title")!!.text()) {
                    "サークル" -> {
                        artist = div.select("div.post-tags > a").joinToString { it.text() }
                    }

                    "作者" -> {
                        author = div.select("div.post-tags > a").joinToString { it.text() }
                    }

                    "内容" -> {
                        genre = div.select("div.post-tags > a").joinToString { it.text() }
                    }

                    else -> {}
                }
            }
            title = document.selectFirst("#post-data > h1")!!.text()
            thumbnail_url = document.selectFirst("#post-hentai > img")?.absUrl("src")
            status = COMPLETED
        }

        val chapter = SChapter.create().apply {
            name = "単一章"
            url = chapterUrl
            date_upload = dateFormat.tryParseDateTime(document.select("#post-time").text())
        }

        return SMangaUpdate(details, listOf(chapter))
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()

        return document.select("#post-hentai > img").mapIndexed { index, element ->
            Page(index, imageUrl = element.attr("src"))
        }
    }

    // Popular

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("div.post-list > a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst("span")!!.text()
                thumbnail_url = element.selectFirst("div.post-list-image > img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, document.selectFirst("div.wp-pagenavi > a.nextpostslink") != null)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/popularity/"))

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder().apply {
            if (query != "" && !query.contains("-")) {
                addQueryParameter("s", query)
            } else {
                val path =
                    filters.filterIsInstance<UriPartFilter>().joinToString("") { it.toUriPart() }
                addEncodedPathSegments(path)
            }
        }

        if (page > 1) {
            urlBuilder.addPathSegments("page/$page")
        }

        return parseMangaList(client.get(urlBuilder.build()))
    }

    // Filters

    override fun getFilterList(data: JsonElement?) = FilterList(
        CategoryGroupFiler(),
    )

    private class CategoryGroupFiler :
        UriPartFilter(
            "カテゴリーグループ",
            arrayOf(
                Pair("同人誌", "fanzine"),
                Pair("商業誌", "magazine"),
                Pair("急上昇", "trend"),
                Pair("人気", "popularity"),
                Pair("高評価", "rated"),
            ),
        )

    private open class UriPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
        defaultValue: Int = 0,
    ) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue) {
        open fun toUriPart() = vals[state].second
    }
}
