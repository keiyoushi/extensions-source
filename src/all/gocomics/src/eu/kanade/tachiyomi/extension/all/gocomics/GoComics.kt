package eu.kanade.tachiyomi.extension.all.gocomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class GoComics : KeiSource() {

    private val rscHeaders get() = headersBuilder().set("RSC", "1").build()

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(fetchFeatures().map { it.toSManga() }, false)

    // The comics with a strip out today
    override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(fetchFeatures().filter { it.updatedToday }.map { it.toSManga() }, false)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(fetchFeatures().filter { it.matches(query.trim()) }.map { it.toSManga() }, false)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.first()
        return fetchFeatures().firstOrNull { it.slug == slug }?.toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) getDetails(manga) else manga }
        val chapterList = async { if (fetchChapters) getStrips(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun getDetails(manga: SManga): SManga {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return document.select("script[type=application/ld+json]")
            .first { "\"ComicSeries\"" in it.data() }
            .data()
            .parseAs<ComicSeriesDto>()
            .toSManga()
            .apply { url = manga.url }
    }

    // Guests can read today's strip and those of the previous 13 days, the archive is for subscribers
    private suspend fun getStrips(manga: SManga): List<SChapter> {
        val today = LocalDate.now(SITE_ZONE)
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/service/v2/assets/feature-runs")
            .addPathSegment(manga.url.removePrefix("/"))
            .addQueryParameter("dateAfter", today.minusDays(FREE_DAYS - 1).toString())
            .addQueryParameter("dateBefore", today.toString())
            .build()
        val chapterName = DateTimeFormatter.ofPattern(if (lang == "es") "d 'de' MMMM 'de' yyyy" else "MMMM d, yyyy", Locale.forLanguageTag(lang))

        return client.get(url, Fetch.XHR.from(getMangaUrl(manga))).parseAs<FeatureRunsDto>().dates
            .map { LocalDateTime.parse(it).toLocalDate() }
            .sortedDescending()
            .map { date ->
                SChapter.create().apply {
                    this.url = "${manga.url}/${date.format(PATH_DATE)}"
                    name = date.format(chapterName)
                    // Scaled down, so that days without a strip don't count as missing chapters
                    chapter_number = date.toEpochDay() / 100_000f
                    date_upload = date.atStartOfDay(SITE_ZONE).toInstant().toEpochMilli()
                }
            }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        // The site shows its offer to subscribe instead of a strip from the archive
        if (document.selectFirst("[data-testid=paywall-contents]") != null) return emptyList()

        return listOf(Page(0, imageUrl = document.selectFirst("meta[property=og:image]")!!.attr("content")))
    }

    // The A to Z page lists every comic, in both languages
    private suspend fun fetchFeatures(): List<FeatureDto> = client.get("$baseUrl/comics/a-to-z", rscHeaders, Fetch.XHR).extractNextJs<CatalogueDto>()!!
        .groupedFeatures
        .flatMap { it.items }
        .filter { it.featureLanguage == if (lang == "es") "Spanish" else "English" }

    companion object {
        private const val FREE_DAYS = 14L
        private val SITE_ZONE = ZoneId.of("America/New_York")
        private val PATH_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd")
    }
}
