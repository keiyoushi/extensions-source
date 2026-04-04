package eu.kanade.tachiyomi.extension.ja.drecomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.clipstudioreader.ClipStudioReader
import keiyoushi.utils.firstInstance
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class DreComics : ClipStudioReader() {
    override val name = "DRE Comics"
    override val baseUrl = "https://drecom-media.jp"
    override val lang = "ja"
    override val supportsLatest = false

    private val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.JAPAN)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/drecomics/series", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".seriesList__item").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.selectFirst("a")!!.absUrl("href"))
                title = it.selectFirst(".seriesList__text")!!.text()
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("t", "2")
            addQueryParameter("page", page.toString())

            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }

            val selectedLabels = filters.firstInstance<LabelFilter>().state.filter { it.state }
            selectedLabels.forEach { addQueryParameter("l[]", it.value) }
            if (selectedLabels.isEmpty()) {
                addQueryParameter("l[]", "3")
                addQueryParameter("l[]", "1")
            }

            filters.firstInstance<GenreFilter>().state.filter { it.state }.forEach {
                addQueryParameter("g[]", it.value)
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.seriesColumn__list li.seriesColumn__item").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.selectFirst("a.seriesColumn__linkButton")!!.absUrl("href"))
                title = it.selectFirst("span.seriesColumn__title")!!.text()
                thumbnail_url = it.selectFirst("img.seriesColumn__img")?.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst("a.pagination__link-next--active") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val isWebtoon = response.request.url.pathSegments.contains("drestudios")

        return SManga.create().apply {
            if (isWebtoon) {
                title = document.selectFirst(".detailStudios_title span")!!.text()
                author = document.select(".detailStudios_author p").eachText().joinToString()
                description = document.selectFirst(".detailStudios_storySynopsis")?.text()
                genre = document.select(".seriesDetail__genreLink--studios").eachText().joinToString()
                thumbnail_url = document.selectFirst(".detailStudios_mainLeft img.img-fluid")?.absUrl("src")
            } else {
                title = document.selectFirst(".detailComics_title span")!!.text()
                author = document.select(".detailComics_authorsItem").eachText().joinToString()
                description = document.selectFirst(".detailComics_synopsis")?.text()
                genre = document.select(".detailComics_genreListItem").eachText().joinToString()
                thumbnail_url = document.selectFirst(".detailComicsSection .img-fluid")?.absUrl("src")
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val isWebtoon = response.request.url.pathSegments.contains("drestudios")

        if (isWebtoon) {
            return document.select(".detailStudios_ebookList_item").map {
                SChapter.create().apply {
                    name = it.selectFirst(".detailStudios_ebookList_itemTitle")!!.text()
                    setUrlWithoutDomain(it.absUrl("href"))
                }
            }.reversed()
        }

        return document.select("div.ebookListItem:not(.disabled):not(.product)").map {
            SChapter.create().apply {
                val link = it.selectFirst("a.ebookListItem_title")!!
                name = link.text()
                setUrlWithoutDomain(link.absUrl("href"))
                date_upload = dateFormat.tryParse(it.selectFirst(".ebookListItem_publishDate span")?.text()?.substringAfter("公開："))
            }
        }
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Note: Search and active filters are applied together"),
        LabelFilter(),
        GenreFilter(),
    )

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
}
