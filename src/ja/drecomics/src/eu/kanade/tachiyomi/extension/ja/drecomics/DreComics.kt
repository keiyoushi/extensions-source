package eu.kanade.tachiyomi.extension.ja.drecomics

import eu.kanade.tachiyomi.multisrc.clipstudioreader.ClipStudioReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class DreComics : ClipStudioReader(
    "DRE Comics",
    "https://drecom-media.jp",
    "ja",
) {
    private val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.JAPAN)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/drecomics/series", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".seriesList__item").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.selectFirst("a")!!.attr("href"))
                title = it.selectFirst(".seriesList__text")!!.text()
                thumbnail_url = it.selectFirst("img")?.attr("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filter = filters.firstInstance<TypeFilter>()
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(filter.toUriPart().removePrefix("/"))
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.toString().contains("/drestudios")) {
            val document = response.asJsoup()
            val mangas = document.select(".seriesLineupStudios_item").map {
                SManga.create().apply {
                    setUrlWithoutDomain(it.attr("href"))
                    title = it.selectFirst(".seriesLineupStudios_itemTitle")!!.text()
                    thumbnail_url = it.selectFirst("img")?.attr("src")
                }
            }
            return MangasPage(mangas, false)
        }
        return popularMangaParse(response)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val isWebtoon = response.request.url.toString().contains("/drestudios")

        return SManga.create().apply {
            if (isWebtoon) {
                title = document.selectFirst(".detailStudios_title span")!!.text()
                author = document.select(".detailStudios_author p").eachText().joinToString(", ")
                description = document.selectFirst(".detailStudios_storySynopsis")?.text()
                genre = document.select(".seriesDetail__genreLink--studios").eachText().joinToString(", ")
                thumbnail_url = document.selectFirst(".detailStudios_mainLeft img.img-fluid")?.attr("src")
            } else {
                title = document.selectFirst(".detailComics_title span")!!.text()
                author = document.select(".detailComics_authorsItem").eachText().joinToString(", ")
                description = document.selectFirst(".detailComics_synopsis")?.text()
                genre = document.select(".detailComics_genreListItem").eachText().joinToString(", ")
                thumbnail_url = document.selectFirst(".detailComicsSection .img-fluid")?.attr("src")
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val isWebtoon = response.request.url.toString().contains("/drestudios")

        if (isWebtoon) {
            return document.select(".detailStudios_ebookList_item").map {
                SChapter.create().apply {
                    name = it.selectFirst(".detailStudios_ebookList_itemTitle")!!.text()
                    setUrlWithoutDomain(it.attr("abs:href"))
                }
            }.reversed()
        }

        return document.select("div.ebookListItem:not(.disabled) a.ebookListItem_title").map {
            SChapter.create().apply {
                name = it.selectFirst(".ebookListItem_title")!!.text()
                setUrlWithoutDomain(it.selectFirst("a")!!.attr("href"))
                date_upload = dateFormat.tryParse(it.selectFirst(".ebookListItem_publishDate span")?.text()?.substringAfter("公開："))
            }
        }
    }

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    override fun getFilterList(): FilterList {
        return FilterList(
            TypeFilter(),
        )
    }

    private class TypeFilter : UriPartFilter(
        "Type",
        arrayOf(
            Pair("Manga", "/drecomics/series"),
            Pair("Webtoon", "/drestudios"),
        ),
    )
}
