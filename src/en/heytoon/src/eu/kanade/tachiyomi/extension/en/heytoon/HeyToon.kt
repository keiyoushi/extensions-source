package eu.kanade.tachiyomi.extension.en.heytoon

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HeyToon : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page != 1) {
            return getSearchMangaList(page - 1, "", SortFilter.popular)
        }

        val document = client.get(baseUrl).asJsoup()

        val entries = document.select("section[class*=slider]:has(h2:matches((?i)popular|trending)) a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.text()
                thumbnail_url = element.selectFirst("img[alt!=badge]")
                    ?.absUrl("data-src")
            }
        }

        return MangasPage(entries, hasNextPage = true)
    }

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", SortFilter.latest)

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Doesn't work with text search"),
        Filter.Separator(),
        SortFilter(),
        GenreFilter(),
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            return querySearch(query)
        }

        val url = "$baseUrl/en/genres".toHttpUrl().newBuilder().apply {
            filters.firstInstanceOrNull<GenreFilter>()?.selected?.also { genre ->
                addPathSegment(genre)
            }
            filters.firstInstance<SortFilter>().sort.also { sort ->
                addQueryParameter("orderBy", sort)
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        val document = client.get(url).asJsoup()

        val entries = document.select("div[class*=comicItem] a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                with(element.selectFirst("img[alt!=badge]")!!) {
                    title = attr("title")
                    thumbnail_url = absUrl("data-src")
                }
            }
        }

        val hasNextPage = document.selectFirst(".wp-pagenavi .nextpostslink") != null

        return MangasPage(entries, hasNextPage)
    }

    private suspend fun querySearch(query: String): MangasPage {
        val url = "$baseUrl/api/complete-search".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query)
            .build()

        val ajaxHeaders = headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        val data = client.get(url, ajaxHeaders).parseAs<List<Comic>>()

        val entries = data.map { comic ->
            SManga.create().apply {
                setUrlWithoutDomain(comic.url)
                title = comic.title
                thumbnail_url = comic.cover
            }
        }

        return MangasPage(entries, hasNextPage = false)
    }

    @Serializable
    class Comic(
        @SerialName("linkComic") val url: String,
        val title: String,
        @SerialName("raw_thumb") val cover: String? = null,
    )

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val details = SManga.create().apply {
            title = document.selectFirst("#titleSubWrapper h1.titCon")!!.text()
            description = document.selectFirst("#modal_detail .cont_area p")?.text()
            genre = document.select("#modal_detail a[href*=genres]").eachText().joinToString()
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            status = with(document.select(".badgeArea span").eachText()) {
                if (contains("Up")) {
                    SManga.ONGOING
                } else if (contains("Completed")) {
                    SManga.COMPLETED
                } else {
                    SManga.UNKNOWN
                }
            }
        }

        val chapterList = document.select(".episodeListConPC a#episodeItemCon").map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                name = it.selectFirst(".comicInfo p.episodeStitle")!!.text()
                date_upload = dateFormat.tryParseDate(it.selectFirst(".comicInfo .episodeDate")?.text())
            }
        }.asReversed()

        return SMangaUpdate(details, chapterList)
    }

    private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        return document.select("#comicContent img").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }
}
