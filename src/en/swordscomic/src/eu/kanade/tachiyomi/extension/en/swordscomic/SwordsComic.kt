package eu.kanade.tachiyomi.extension.en.swordscomic

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class SwordsComic : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(TextInterceptor())
    }

    private fun createManga(): SManga = SManga.create().apply {
        title = "Swords Comic"
        url = "/archive/pages/"
        author = "Matthew Wills"
        artist = author
        description = "A webcomic about swords and the heroes who wield them"
        thumbnail_url = "https://swordscomic.com/media/ArgoksEdgeEmote.png"
    }

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(createManga()), false)

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(listOf(createManga()), false)

    // Updates

    private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val manga = createManga().apply { initialized = true }

        val chapters = if (fetchChapters) {
            client.get(getMangaUrl(manga)).asJsoup()
                .select("a.archive-tile")
                .map { element ->
                    SChapter.create().apply {
                        name = element.selectFirst("strong")!!.text()
                        setUrlWithoutDomain(element.attr("abs:href"))
                        date_upload = element.selectFirst("small")?.text()
                            .let { dateFormat.tryParseDate(it) }
                    }
                }
                .reversed()
        } else {
            chapters
        }

        return SMangaUpdate(manga, chapters)
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val imageElement = client.get(getChapterUrl(chapter)).asJsoup().select("img#comic-image")
        if (!imageElement.hasAttr("title")) {
            return listOf(Page(0, "", imageElement.attr("abs:src")))
        }
        val titleText = TextInterceptorHelper.createUrl("", imageElement.attr("title"))

        return listOf(Page(0, "", imageElement.attr("abs:src")), Page(1, "", titleText))
    }
}
