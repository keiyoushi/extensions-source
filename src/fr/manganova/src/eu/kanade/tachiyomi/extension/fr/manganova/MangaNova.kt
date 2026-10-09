package eu.kanade.tachiyomi.extension.fr.manganova

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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class MangaNova : KeiSource() {

    val apiUrl = "https://api.manga-nova.com"

    override fun OkHttpClient.Builder.configureClient() = addInterceptor { chain ->
        val req = chain.request()
        if (req.url.host != apiUrl.toHttpUrl().host) {
            return@addInterceptor chain.proceed(req)
        }
        val auth = bearerHeader()
        chain.proceed(req.newBuilder().header("Authorization", auth).build())
    }

    // Popular
    override suspend fun getPopularManga(page: Int) = parseSearch(client.get("$apiUrl/catalogue/"))

    // Latest
    override suspend fun getLatestUpdates(page: Int) = client.get(
        "$apiUrl/catalogue/",
    ).parseAs<Catalogue>().newSeries.map {
        it.toDetailedSManga()
    }.let { MangasPage(it, false) }

    // Search
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        if (url.pathSegments.count(String::isNotBlank) < 2) return null

        val slug = url.pathSegments[1]
        val catalogue = client.get(
            "$apiUrl/catalogue/",
        ).parseAs<Catalogue>()
        return catalogue.series.find {
            it.slug == slug
        }?.toDetailedSManga()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            "$apiUrl/catalogue/#$query"
        } else {
            "$apiUrl/catalogue/"
        }
        return parseSearch(client.get(url), query)
    }

    private fun parseSearch(
        response: Response,
        query: String = "",
    ): MangasPage {
        val catalogue = response.parseAs<Catalogue>()
        val mangaList = mutableListOf<SManga>()

        for (serie in catalogue.series) {
            if (query.isBlank() ||
                serie.title.contains(query, ignoreCase = true) ||
                serie.titleJap.contains(query, ignoreCase = true)
            ) {
                mangaList.add(serie.toDetailedSManga())
            }
        }

        return MangasPage(mangaList, false)
    }

    // MangaUpdate
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (fetchDetails) getMangaDetails(manga) else manga
        }
        val chapterList = async {
            if (fetchChapters) getChapterList(manga) else chapters
        }
        SMangaUpdate(details.await(), chapterList.await())
    }

    // Details
    private suspend fun getMangaDetails(manga: SManga): SManga {
        val slug = manga.url.slug()
        val catalogue = client.get(
            "$apiUrl/catalogue/",
        ).parseAs<Catalogue>()
        val series = catalogue.series
        val serie = series.find { it.slug == slug }
        if (serie == null) error("Bad SLUG")

        return serie.toDetailedSManga()
    }

    // Chapters
    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val slug = manga.url.slug()
        val response = client.get("$apiUrl/mangas/$slug")
        val serie = response.parseAs<DetailedSerieContainer>().serie
        val categories = serie.chapitres
        val chapterList = mutableListOf<SChapter>()

        val currentEpoch = System.currentTimeMillis()
        for (category in categories) {
            for (chapter in category.chapitres) {
                if (chapter.amount != 0) continue

                val chapter = SChapter.create().apply {
                    name = category.title + " - " + chapter.title + " - " + chapter.subTitle
                    setUrlWithoutDomain("$baseUrl/lecture-en-ligne/${serie.slug}/chapitre/${chapter.number}")
                    chapter_number = chapter.number
                    date_upload = currentEpoch + (chapter.availableTime * 1000L)
                }
                chapterList.add(chapter)
            }
        }

        return chapterList.sortedByDescending { it.chapter_number }
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val slug = chapter.url.slug()
        val chapterNumber = chapter.url.number()
        val response = client.get("$apiUrl/mangas/$slug/chapitres/$chapterNumber")

        val images = response.parseAs<ChapterDetails>().images
        return images.map { pageData ->
            Page(pageData.pageNumber, imageUrl = pageData.image)
        }
    }

    // Utils
    private fun bearerHeader(): String {
        val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
        val token = cookies.firstOrNull { it.name == "token" }?.value
            ?: DEFAULT_TOKEN
        return "Bearer $token"
    }

    private fun String.slug() = "$baseUrl$this".toHttpUrl().pathSegments.get(1)
    private fun String.number() = "$baseUrl$this".toHttpUrl().pathSegments.last()

    companion object {
        // Default static token, shouldn't change
        private const val DEFAULT_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJtZW1icmVfaWQiOjAsIm1lbWJyZV91c2VybmFtZSI6bnVsbCwiaWF0IjoxNzA1NTc5MDQ1fQ.51qivLd2l3OKbDaYYzlntZJNnreRSBWO7p5Nsa2mAsA"
    }
}
