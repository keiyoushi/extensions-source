package eu.kanade.tachiyomi.extension.ja.jumptoon

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.getValue

@Source
abstract class JumpToon :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()
    private val rscHeaders get() = headersBuilder()
        .set("rsc", "1")
        .build()

    private val currentDayOfWeek: String
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
        val document = client.get("$baseUrl/series/original/$currentDayOfWeek/").asJsoup()
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
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val details = async {
            if (!fetchDetails) return@async manga
            val document = client.get(getMangaUrl(manga)).asJsoup()
            val series = document.extractNextJs<SeriesDetails> { it is JsonObject && "seriesStatusType" in it } ?: return@async manga
            val genres = document.select("h1 ~ div > span").map { it.text() }
            series.toSManga(genres)
        }

        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val firstPage = getEpisodePage(manga, 1) ?: return@async emptyList()
            val otherPages = (2..firstPage.totalPageCount)
                .map { page -> async { getEpisodePage(manga, page) } }
                .awaitAll()

            (listOf(firstPage) + otherPages.filterNotNull())
                .flatMap { it.episodes.edges }
                .filter { !hideLocked || !it.node.isLocked }
                .map { it.node.toSChapter() }
        }

        SMangaUpdate(
            details.await(),
            chapterList.await(),
        )
    }

    private suspend fun getEpisodePage(manga: SManga, page: Int): EpisodeListResponse? {
        val url = "${getMangaUrl(manga)}episodes/".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "DESC")
            .addQueryParameter("page", page.toString())
            .build()

        return client.get(url, rscHeaders).extractNextJs<EpisodeListResponse>()
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/series/${chapter.memo["seriesId"]!!.string}/episodes/${chapter.url}/"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val content = client.get(getChapterUrl(chapter), rscHeaders).extractNextJs<EpisodeContent>()
            ?: throw Exception("Log in via WebView and rent or purchase this chapter to read.")

        val seed = "${content.seriesId}:${content.number}".sumOf { it.code }
        return content.pageList.mapIndexed { i, page ->
            Page(i, imageUrl = "${page.imageUrl}#${content.scrambleAlgorithmType}:$seed:${page.width}")
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
