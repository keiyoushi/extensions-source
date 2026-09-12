package eu.kanade.tachiyomi.extension.ja.jumptoon

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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.get
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Source
abstract class JumpToon : KeiSource() {
    private val rscHeaders get() = headersBuilder()
        .set("rsc", "1")
        .build()

    private val dayOfWeek: String
        get() = LocalDate.now(ZoneId.of("Asia/Tokyo")).dayOfWeek.name.lowercase(Locale.US)

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        val cards = client.get("$baseUrl/series/ranking/overall/", rscHeaders).extractNextJs<List<JsonElement>> { element ->
            element is JsonArray && element.isNotEmpty() && element.all { it.elementProps()?.containsKey("seriesRanking") == true }
        }
        val mangas = cards.orEmpty().map { it.elementProps()!!.parseAs<RankingResponse>().seriesRanking.series.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/series/original/$dayOfWeek/").asJsoup()
        val mangas = document.select("main li:has(a[href^=/series/])").map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addPathSegment(query)
            .addPathSegment("")
            .addQueryParameter("page", page.toString())
            .build()

        val document = client.get(url).asJsoup()
        val mangas = document.select("#load-searchResultList > li").map { it.toSManga() }
        val hasNextPage = document.selectFirst("a[rel=next]:not([aria-disabled])") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun JsonElement.elementProps(): JsonObject? = (this as? JsonArray)?.getOrNull(3) as? JsonObject

    private fun Element.toSManga(): SManga = SManga.create().apply {
        val link = selectFirst("a[href^=/series/]")!!
        url = link.absUrl("href").toHttpUrl().pathSegments[1]
        title = link.text()
        thumbnail_url = selectFirst("img")?.absUrl("src")?.toHttpUrl()?.newBuilder()?.query(null)?.build().toString()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}/"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (!fetchDetails) return@async manga
            val document = client.get(getMangaUrl(manga)).asJsoup()
            val series = document.extractNextJs<SeriesDetails>() ?: return@async manga
            val genres = document.extractNextJs<List<JsonElement>> { element ->
                element is JsonArray && element.isNotEmpty() && element.all { it.genreLabel() != null }
            }

            series.toSManga(genres.orEmpty().mapNotNull { it.genreLabel() })
        }

        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val episodes = mutableListOf<SChapter>()
            var page = 1
            var totalPageCount = 1
            while (page <= totalPageCount) {
                val url = "${getMangaUrl(manga)}episodes/".toHttpUrl().newBuilder()
                    .addQueryParameter("sort", "DESC")
                    .addQueryParameter("page", page.toString())
                    .build()

                val episodePage = client.get(url, rscHeaders).extractNextJs<EpisodeListResponse>() ?: break
                totalPageCount = episodePage.totalPageCount
                episodes += episodePage.episodes.edges.map { it.node.toSChapter() }
                page++
            }
            episodes
        }

        SMangaUpdate(
            details.await(),
            chapterList.await(),
        )
    }

    private fun JsonElement.genreLabel(): String? = (this as? JsonArray)
        ?.takeIf { (it.getOrNull(1) as? JsonPrimitive)?.contentOrNull == "span" }
        ?.let { it.getOrNull(3) as? JsonObject }
        ?.get("children")
        ?.let { it as? JsonPrimitive }
        ?.contentOrNull

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/series/${chapter.memo["seriesId"]!!.string}/episodes/${chapter.url}/"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val content = client.get(getChapterUrl(chapter), rscHeaders).extractNextJs<EpisodeContent>()
            ?: throw Exception("Log in via WebView and purchase this chapter to read.")

        val seed = "${content.seriesId}:${content.number}".sumOf { it.code }
        return content.pageList.mapIndexed { i, page ->
            Page(i, imageUrl = "${page.imageUrl}#${content.scrambleAlgorithmType}:$seed:${page.width}")
        }
    }
}
