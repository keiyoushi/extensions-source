package eu.kanade.tachiyomi.extension.ja.nikkangecchan

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
import okhttp3.Request

@Source
abstract class Nikkangecchan : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select(".contentInner > figure").mapNotNull { element ->
            val imgBox = element.selectFirst(".imgBox")
            val detailBox = element.select(".detailBox").lastOrNull()

            val mangaTitle = detailBox?.selectFirst("h3")?.text() ?: return@mapNotNull null
            val mangaUrl = imgBox?.selectFirst("a")?.attr("abs:href") ?: return@mapNotNull null

            SManga.create().apply {
                title = mangaTitle
                thumbnail_url = imgBox.selectFirst("a > img")?.attr("abs:src")
                setUrlWithoutDomain(mangaUrl)
            }
        }

        return MangasPage(mangas, false)
    }

    // Does not have search, use complete list (in popular) instead.
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val filtered = getPopularManga(page).mangas.filter { it.title.contains(query, ignoreCase = true) }
        return MangasPage(filtered, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        val detailBox = document.selectFirst("#comicDetail .detailBox")
            ?: throw Exception("Detail box not found")

        val details = SManga.create().apply {
            title = detailBox.selectFirst("h3")?.text() ?: ""
            author = detailBox.selectFirst(".author")?.text()
            artist = author
            description = document.selectFirst(".description")?.text()
            status = SManga.ONGOING
        }

        val chapterList = document.select(".episodeBox").mapNotNull { element ->
            val episodePage = element.selectFirst(".episode-page") ?: return@mapNotNull null
            val title = element.selectFirst("h4.episodeTitle")?.text() ?: return@mapNotNull null
            val dataTitle = episodePage.attr("data-title").substringBefore("|").trim()

            SChapter.create().apply {
                name = if (dataTitle.isNotEmpty()) "$title - $dataTitle" else title
                chapter_number = title.toFloatOrNull() ?: -1f
                scanlator = "Akita Publishing"

                val dataSrc = episodePage.attr("abs:data-src").ifEmpty { baseUrl + episodePage.attr("data-src") }
                setUrlWithoutDomain(dataSrc.substringBeforeLast("/"))
            }
        }.reversed()

        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = listOf(
        Page(0, url = chapter.url, imageUrl = "$baseUrl${chapter.url}/image"),
    )

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .header("Referer", baseUrl + page.url.substringBeforeLast("/"))
        .build()

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()
}
