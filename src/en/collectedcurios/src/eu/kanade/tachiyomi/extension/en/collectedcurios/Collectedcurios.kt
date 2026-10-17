package eu.kanade.tachiyomi.extension.en.collectedcurios

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

@Source
abstract class Collectedcurios : KeiSource() {

    override val supportsLatest = false

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(
        arrayListOf(
            SManga.create().apply {
                title = "Sequential Art"
                artist = "Jolly Jack aka Phillip M Jackson"
                author = "Jolly Jack aka Phillip M Jackson"
                status = SManga.ONGOING
                url = "/sequentialart.php"
                description = "Sequential Art webcomic."
                thumbnail_url = "https://www.collectedcurios.com/images/CC_2011_Sequential_Art_Button.jpg"
            },

            SManga.create().apply {
                title = "Battle Bunnies"
                artist = "Jolly Jack aka Phillip M Jackson"
                author = "Jolly Jack aka Phillip M Jackson"
                status = SManga.ONGOING
                url = "/battlebunnies.php"
                description = "Battle Bunnies webcomic."
                thumbnail_url = "https://www.collectedcurios.com/images/CC_2011_Battle_Bunnies_Button.jpg"
            },

            SManga.create().apply {
                title = "Spider and Scorpion"
                artist = "Jolly Jack aka Phillip M Jackson"
                author = "Jolly Jack aka Phillip M Jackson"
                status = SManga.ONGOING
                url = "/spiderandscorpion.php"
                description = "Spider and Scorpion webcomic."
                thumbnail_url = "https://www.collectedcurios.com/images/CC_2011_Spider_And_Scorpion_Button.jpg"
            },
        ),
        false,
    )

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getPopularManga(1)

    // ============================== Chapters ==============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        val responseJs = client.get(getMangaUrl(manga)).asJsoup()

        val lastChapter = responseJs.selectFirst("img[title=Last]")?.parent()
            ?.attr("href")?.substringAfter("=")?.toIntOrNull()
            ?: responseJs.selectFirst("input[title=Jump to number]")
                ?.attr("value")?.toIntOrNull()
            ?: responseJs.selectFirst("img[title=Back one]")?.parent()
                ?.attr("href")?.substringAfter("=")?.toIntOrNull()?.plus(1)
            ?: 1

        val chapterList = (lastChapter downTo 1).map { i ->
            SChapter.create().apply {
                url = "${manga.url}?s=$i"
                name = "Chapter - $i"
                chapter_number = i.toFloat()
                date_upload = 0L
            }
        }

        return SMangaUpdate(manga, chapterList)
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> = listOf(Page(0, baseUrl + chapter.url))

    override suspend fun getImageUrl(page: Page): String {
        val url = page.url
        val document = client.get(url).asJsoup()

        return when {
            url.contains("sequentialart") ->
                document.selectFirst(".w3-image")!!.absUrl("src")

            url.contains("battlebunnies") || url.contains("spiderandscorpion") ->
                document.selectFirst("#strip")!!.absUrl("src")

            else -> throw Exception("Could not find the image")
        }
    }
}
