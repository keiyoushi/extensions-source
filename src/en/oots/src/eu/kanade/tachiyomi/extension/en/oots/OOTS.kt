package eu.kanade.tachiyomi.extension.en.oots

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
import keiyoushi.utils.getPreferencesLazy

@Source
abstract class OOTS : KeiSource() {

    override val supportsLatest = false

    private val preferences by getPreferencesLazy()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val manga = SManga.create().apply {
            title = "The Order Of The Stick"
            artist = "Rich Burlew"
            author = "Rich Burlew"
            status = SManga.ONGOING
            url = "/comics/oots.html"
            description = "Having fun with games."
            thumbnail_url = "https://i.giantitp.com/redesign/Icon_Comics_OOTS.gif"
            initialized = true
        }

        return MangasPage(listOf(manga), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(emptyList(), false)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        val document = client.get(getMangaUrl(manga)).asJsoup()
        val elements = document.select("p.ComicList a")

        val currentTimeMillis = System.currentTimeMillis()
        val prefs = preferences
        val editor = prefs.edit()

        val chapterList = elements.map { element ->
            SChapter.create().apply {
                url = element.attr("href")
                name = element.text()

                val numberMatch = NUMBER_REGEX.find(url)
                val numberStr = numberMatch?.groupValues?.get(1) ?: ""
                chapter_number = numberStr.toFloatOrNull() ?: -1f

                if (numberStr.isNotEmpty()) {
                    if (!prefs.contains(numberStr)) {
                        editor.putLong(numberStr, currentTimeMillis)
                    }
                    date_upload = prefs.getLong(numberStr, currentTimeMillis)
                } else {
                    date_upload = currentTimeMillis
                }
            }
        }.distinctBy { it.url }

        editor.apply()

        return SMangaUpdate(manga, chapterList.reversed())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val imageUrl = document.select("td[align='center'] > img").attr("abs:src")
        return listOf(Page(0, imageUrl = imageUrl))
    }

    companion object {
        private val NUMBER_REGEX = """oots(\d+)\.html""".toRegex()
    }
}
