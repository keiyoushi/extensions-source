package eu.kanade.tachiyomi.extension.ja.gaugaumonsterplus

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.speedbinb.SpeedBinbInterceptor
import keiyoushi.lib.speedbinb.fetchPages
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.string
import keiyoushi.utils.textOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class GaugauMonsterPlus : KeiSource() {
    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(SpeedBinbInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage = client.get("$baseUrl/list/works?page=$page").toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val tagFilter = filters.firstInstance<Filters>()
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegments("list/search-result")
                addQueryParameter("word", query)
            } else if (tagFilter.state != 0) {
                addPathSegments("list/tag")
                addPathSegment(tagFilter.values[tagFilter.state])
            } else {
                addPathSegments("list/works")
            }

            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return client.get(url).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val document = this.asJsoup()
        val mangas = document.select(".works__grid .list__box").map {
            SManga.create().apply {
                val link = it.selectFirst("h4 a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = it.selectFirst(".thumbnail .img-books")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("ol.pagination li.next a:not([href='#'])") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl${manga.url}/episodes").asJsoup()
        val details = SManga.create().apply {
            title = document.selectFirst(".mbOff h1")!!.text()
            author = document.select(".list__text span a").joinToString { it.text() }
            description = document.selectFirst("p.mbOff")?.textOrNull()
            genre = document.select(".list__text .tag__item").joinToString { it.text() }
            thumbnail_url = document.selectFirst(".list__box .thumbnail .img-books")?.absUrl("src")
        }

        val chapterList = document.select("#episodes .episode__grid:not(:has(.episode__button-app, .episode__button-complete)) a").map {
            SChapter.create().apply {
                val episodeNum = it.selectFirst(".episode__num")!!.text()
                val episodeTitle = it.selectFirst(".episode__title")?.textOrNull()
                val segments = it.absUrl("href").toHttpUrl().pathSegments

                url = segments[4]
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
                memo = buildJsonObject {
                    put("work", segments[2])
                }
            }
        }

        return SMangaUpdate(
            details,
            chapterList,
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/list/work/${chapter.memo["work"]!!.string}/episodes/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.fetchPages(client.get(getChapterUrl(chapter)).asJsoup())

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("フリーワード検索はジャンル検索では機能しません"),
        Filters(),
    )

    companion object {
        private val CHAPTER_NUMBER_REGEX = Regex("""^第(\d+)話\((\d+)\)$""")
    }
}
