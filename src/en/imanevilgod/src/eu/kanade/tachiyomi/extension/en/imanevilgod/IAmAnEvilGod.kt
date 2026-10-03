package eu.kanade.tachiyomi.extension.en.imanevilgod

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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class IAmAnEvilGod : KeiSource() {

    override val supportsLatest = false

    // --- Catalogue (single entry) ---

    private fun createManga() = SManga.create().apply {
        title = "I'm An Evil God"
        url = "/"
        status = SManga.UNKNOWN
    }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(createManga()), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(listOf(createManga()), false)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = createManga().takeIf { url.host == baseUrl.toHttpUrl().host }

    // --- Manga details & chapter list ---

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(baseUrl).asJsoup()

        val details = SManga.create().apply {
            title = "I'm An Evil God"
            url = "/"
            status = SManga.UNKNOWN
            description = "Across the realms, the manliest and most handsome evil god in history! " +
                "Xie Yan crosses over and falls into the vixen's lair..."
            thumbnail_url = doc.selectFirst("meta[property=og:image]")?.attr("content")
        }

        // Chapters are <a> tags inside the paragraph with class "has-medium-font-size"
        val chapterList = doc.select("p.has-medium-font-size a[href*=imanevilgod.com]")
            .mapIndexed { index, el ->
                SChapter.create().apply {
                    name = el.text()
                    setUrlWithoutDomain(el.absUrl("href"))
                    chapter_number = index.toFloat()
                }
            }

        return SMangaUpdate(details, chapterList)
    }

    // --- Page list ---

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val doc = client.get(getChapterUrl(chapter)).asJsoup()
        // Chapter pages are <img> tags inside the post content
        return doc.select("div.entry-content img")
            .mapIndexed { index, el ->
                Page(index, "", el.absUrl("src").ifEmpty { el.absUrl("data-src") })
            }
    }
}
