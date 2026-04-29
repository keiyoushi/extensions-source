package eu.kanade.tachiyomi.extension.ja.gaugaumonsterplus

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.speedbinb.SpeedBinbInterceptor
import keiyoushi.lib.speedbinb.SpeedBinbReader
import keiyoushi.utils.firstInstance
import keiyoushi.utils.jsonInstance
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class GaugauMonsterPlus : HttpSource() {

    override val name = "がうがうモンスター＋"

    override val baseUrl = "https://gaugau.futabanet.jp"

    override val lang = "ja"

    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(SpeedBinbInterceptor(jsonInstance))
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/list/works?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".works__grid .list__box").map { element ->
            SManga.create().apply {
                element.selectFirst("h4 a")!!.let {
                    setUrlWithoutDomain(it.attr("href"))
                    title = it.text()
                }
                author = element.select(".list__text span a").joinToString { it.text() }
                genre = element.select(".tag__item").joinToString { it.text() }
                thumbnail_url = element.selectFirst(".thumbnail .img-books")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("ol.pagination li.next a:not([href='#'])") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tagFilter = filters.ifEmpty { getFilterList() }.firstInstance<Filters>()
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addPathSegments("list/search-result")
                addQueryParameter("word", query)
            } else if (tagFilter.state != 0) {
                addPathSegments("list/tag")
                addPathSegment(tagFilter.values[tagFilter.state])
            } else {
                addPathSegment("list/works")
            }

            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".mbOff h1")!!.text()
            author = document.select(".list__text span a").joinToString { it.text() }
            description = document.selectFirst("p.mbOff")?.text()
            genre = document.select(".tag__item").joinToString { it.text() }
            thumbnail_url = document.selectFirst(".list__box .thumbnail .img-books")?.absUrl("src")
        }
    }

    override fun chapterListRequest(manga: SManga) = GET("$baseUrl${manga.url}/episodes", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("#episodes .episode__grid:not(:has(.episode__button-app, .episode__button-complete)) a").map { element ->
            SChapter.create().apply {
                val episodeNum = element.selectFirst(".episode__num")!!.text()
                val episodeTitle = element.selectFirst(".episode__title")?.text()
                    ?.takeIf { t -> t.isNotEmpty() }

                setUrlWithoutDomain(element.attr("href"))
                name = buildString(episodeNum.length + 2 + (episodeTitle?.length ?: 0)) {
                    append(episodeNum)

                    if (episodeTitle != null) {
                        append("「")
                        append(episodeTitle)
                        append("」")
                    }
                }
                chapter_number = CHAPTER_NUMBER_REGEX.matchEntire(episodeNum)?.let { m ->
                    val major = m.groupValues[1].toFloat()
                    val minor = m.groupValues[2].toFloat()
                    major + minor / 10
                } ?: -1F
            }
        }
    }

    private val reader by lazy { SpeedBinbReader(client, headers, jsonInstance, true) }

    override fun pageListParse(response: Response): List<Page> = reader.pageListParse(response)

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("フリーワード検索はジャンル検索では機能しません"),
        Filters(),
    )

    companion object {
        private val CHAPTER_NUMBER_REGEX = Regex("""^第(\d+)話\((\d+)\)$""")
    }
}
