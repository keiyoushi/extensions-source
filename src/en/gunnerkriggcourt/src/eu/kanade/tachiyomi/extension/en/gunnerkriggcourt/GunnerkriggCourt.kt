package eu.kanade.tachiyomi.extension.en.gunnerkriggcourt

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
abstract class GunnerkriggCourt : KeiSource() {
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        val manga = SManga.create().apply {
            title = name
            artist = "Tom Siddell"
            author = artist
            status = SManga.ONGOING
            url = "/archives/"
            description = """
                Gunnerkrigg Court is a Science Fantasy webcomic by Tom Siddell about a strange young girl attending an equally strange school. The intricate story is deeply rooted in world mythology, but has a strong focus on science (chemistry and robotics, most prominently) as well.

                Antimony Carver begins classes at the eponymous U.K. Boarding School, and soon notices that strange events are happening: a shadow creature follows her around; a robot calls her "Mummy"; a Rogat Orjak smashes in the dormitory roof; odd birds, ticking like clockwork, stand guard in out-of-the-way places.

                Stranger still, in the middle of all this, Annie remains calm and polite to a fault.
            """.trimIndent()
            thumbnail_url = "https://i.imgur.com/g2ukAIKh.jpg"
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

        val chapterList = document.select("div.chapters option[value~=\\d*]").map { element ->
            SChapter.create().apply {
                val chapterNumStr = element.attr("value")
                chapter_number = chapterNumStr.toFloatOrNull() ?: -1f
                setUrlWithoutDomain("/?p=$chapterNumStr")

                val title = element.parent()?.previousElementSibling()?.text() ?: "Chapter"
                name = "$title (${if (chapter_number >= 0f) chapter_number.toInt() else chapterNumStr})"
            }
        }.reversed()

        return SMangaUpdate(manga, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        return document.select(".comic_image").mapIndexed { i, element ->
            Page(i, imageUrl = element.absUrl("src"))
        }
    }
}
