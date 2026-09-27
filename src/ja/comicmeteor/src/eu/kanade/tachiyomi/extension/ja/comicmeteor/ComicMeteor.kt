package eu.kanade.tachiyomi.extension.ja.comicmeteor

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class ComicMeteor : KeiSource() {
    private val apiUrl get() = "$baseUrl/api"
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日").withZone(ZoneId.of("Asia/Tokyo"))

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(SpeedBinbInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage = client.get("$baseUrl/titles").toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("word", query)
                .build()
            return client.get(url).toMangasPage()
        }

        val category = filters.firstInstanceOrNull<AllFilter>()?.selected
        val url = "$baseUrl/titles".toHttpUrl().newBuilder().apply {
            category?.let { addQueryParameter(it.key, it.value) }
        }.build()
        return client.get(url).toMangasPage()
    }

    private suspend fun Response.toMangasPage(): MangasPage {
        val document = this.asJsoup()
        val mangas = document.select("#titles-container a, .content-container .grid-group .w-auto a").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                val img = it.selectFirst("img")!!
                title = img.attr("alt")
                thumbnail_url = img.absUrl("src")
            }
        }

        val readAt = document.selectFirst("#more_titles_button")?.attr("data-read-at")
            ?: return MangasPage(mangas, false)

        val url = "$apiUrl/title-list".toHttpUrl().newBuilder()
            .addQueryParameter("read_at", readAt)
            .apply { request.url.queryParameterNames.forEach { addQueryParameter(it, request.url.queryParameter(it)) } }
            .build()

        val apiMangas = client.get(url).parseAs<ApiTitlesResponse>().data.map { it.toSManga() }
        return MangasPage(mangas + apiMangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val details = SManga.create().apply {
            title = document.selectFirst("main h2")!!.text()
            author = document.select("a[href*=/authors/]").joinToString { it.text() }
            description = document.selectFirst("#plot + div")?.textOrNull()
            genre = document.select("div.pt-5 a.button-gray").joinToString { it.text() }
        }

        val chapterList = document.select(".episodes-container .episode-item")
            .filterNot { it.text().contains("未公開話") }
            .mapNotNull { item ->
                item.selectFirst("a")?.let { link ->
                    SChapter.create().apply {
                        val segments = link.absUrl("href").toHttpUrl().pathSegments
                        url = segments[3]
                        name = item.selectFirst(".episode-item-left")!!.text()
                        memo = buildJsonObject {
                            put("label", segments[1])
                            put("slug", segments[2])
                        }
                    }
                }
            }
            .ifEmpty {
                listOfNotNull(
                    document.selectFirst(".header-side a.episode-read")?.let { link ->
                        SChapter.create().apply {
                            val segments = link.absUrl("href").toHttpUrl().pathSegments
                            url = segments[3]
                            name = document.selectFirst(".latest-episode-title")!!.text()
                            date_upload = dateFormat.tryParseDate(document.selectFirst(".last-update")?.text()?.substringBefore("更新"))
                            memo = buildJsonObject {
                                put("label", segments[1])
                                put("slug", segments[2])
                            }
                        }
                    },
                )
            }

        return SMangaUpdate(
            details,
            chapterList,
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/pt/${chapter.memo["label"]!!.string}/${chapter.memo["slug"]!!.string}/${chapter.url}/viewer"

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.fetchPages(client.get(getChapterUrl(chapter)).asJsoup())

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/titles").asJsoup()
        return listOf("label" to "レーベルから選ぶ", "genre" to "ジャンルから選ぶ", "category" to "カテゴリから選ぶ").flatMap { (key, heading) ->
            document.selectFirst("h3:contains($heading)")?.nextElementSibling()?.select("a").orEmpty().map {
                FilterOption(it.text(), key, it.attr("href").substringAfter("="))
            }
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val options = data?.parseAs<List<FilterOption>>() ?: return FilterList()
        return FilterList(AllFilter(options))
    }

    private class AllFilter(private val options: List<FilterOption>) : Filter.Select<String>("Filter by", arrayOf("All") + options.map { it.name }) {
        val selected get() = options.getOrNull(state - 1)
    }
}
