package eu.kanade.tachiyomi.extension.fr.scanr

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URI

@Source
abstract class ScanR : KeiSource() {

    val cdnUrl = "https://cdn.teamscanr.fr"
    override val supportsLatest = false
    private val seriesDataCache = mutableMapOf<String, Serie>()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        TypeFilter(),
        StatusFilter(),
        AdultFilter(),
    )

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Search
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) {
            return null
        }
        val slug = url.pathSegments[0]
        val filename = fetchIndex()[slug] ?: return null
        return fetchSeriesData(filename).toDetailedSManga()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$cdnUrl/index.json".toHttpUrl().newBuilder()
        filters.filterIsInstance<UriFilter>().forEach {
            it.addToUri(url)
        }
        val params = url.build()

        val series = fetchIndex()
        val mangaList = mutableListOf<SManga>()

        val types = params.queryParameter("type") ?: "all"
        val status = params.queryParameter("status") ?: "all"
        val adult = params.queryParameter("adult") ?: "all"

        for ((_, filename) in series) {
            val serie = fetchSeriesData(filename)

            if (query.isBlank() || serie.title.contains(query, ignoreCase = true)) {
                val details = serie.toDetailedSManga()
                if ((((serie.os && types.contains("os")) || (!serie.os && types.contains("series")) || types.contains("all"))) &&
                    ((((details.status == SManga.ONGOING) && status.contains("ongoing")) || (((details.status == SManga.COMPLETED) && status.contains("completed"))) || status.contains("all"))) &&
                    ((serie.konami && adult.contains("18")) || (!serie.konami && adult.contains("normal")) || adult.contains("all"))
                ) {
                    mangaList.add(details)
                }
            }
        }

        return MangasPage(mangaList, false)
    }

    // Details & Chapters
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = URI(manga.url).path.split("/")[1]
        val serie = fetchSeriesData(fetchIndex()[slug]!!)
        return SMangaUpdate(serie.toDetailedSManga(), buildChapterList(serie))
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val splitedPath = URI(chapter.url).path.split("/")
        val slug = splitedPath[1]
        val chapterId = splitedPath[2]
        val serie = fetchSeriesData(fetchIndex()[slug] ?: "")
        val chapterDetails = serie.chapters[chapterId.replace("-", ".")]
        val cubariProxy = chapterDetails!!.groups.getValue(chapterDetails.groups.keys.first())
        val images = client.get("https://cubari.moe$cubariProxy").parseAs<List<String>>()
        return images.mapIndexed { index, pageData ->
            Page(index, imageUrl = pageData)
        }
    }

    private fun buildChapterList(serie: Serie): List<SChapter> {
        val chapters = serie.chapters
        val chapterList = mutableListOf<SChapter>()

        for ((chapterNumber, chapterData) in chapters) {
            val title = chapterData.title
            val volumeNumber = chapterData.volume

            val baseName = if (!serie.os) {
                buildString {
                    if (volumeNumber.isNotBlank()) append("Vol. $volumeNumber ")
                    append("Ch. $chapterNumber")
                    if (title.isNotBlank()) append(" – $title")
                }
            } else {
                if (title.isNotBlank()) "One Shot – $title" else "One Shot"
            }

            val chapter = SChapter.create().apply {
                name = baseName
                setUrlWithoutDomain("$baseUrl/${serie.slug}/${chapterNumber.replace(".","-")}")
                chapter_number = chapterNumber.toFloatOrNull() ?: -1f
                scanlator = chapterData.groups.keys.first()
                date_upload = chapterData.lastUpdated.toLong() * 1000L
            }
            chapterList.add(chapter)
        }

        return chapterList.sortedByDescending { it.chapter_number }
    }

    // Series utils
    private suspend fun fetchIndex(): Map<String, String> = client.get("$cdnUrl/index.json").parseAs()

    private suspend fun fetchSeriesData(filename: String): Serie {
        seriesDataCache[filename]?.let { return it }

        val seriesData = client.get("$cdnUrl/$filename").parseAs<Serie>()

        seriesDataCache[filename] = seriesData
        return seriesData
    }
}
