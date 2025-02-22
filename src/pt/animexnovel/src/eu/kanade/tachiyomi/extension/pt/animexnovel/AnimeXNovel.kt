package eu.kanade.tachiyomi.extension.pt.animexnovel

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

class AnimeXNovel : ZeistManga(
    "AnimeXNovel",
    "https://www.animexnovel.com",
    "pt-BR",
) {

    // ============================== Popular ===============================

    override val popularMangaSelector = "#PopularPosts2 article"
    override val popularMangaSelectorTitle = "h3 > a"
    override val popularMangaSelectorUrl = popularMangaSelectorTitle

    override fun popularMangaParse(response: Response): MangasPage {
        return super.popularMangaParse(response).let { mangaPage ->
            mangaPage.mangas.filter { it.title.contains("[Mangá]") }.let {
                mangaPage.copy(it)
            }
        }
    }

    // ============================== Latest ===============================

    override fun latestUpdatesParse(response: Response): MangasPage {
        return super.latestUpdatesParse(response).let {
            it.copy(it.mangas.filter { it.title.contains("[Mangá]") })
        }
    }

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst(".thumb")?.absUrl("src")
        description = document.selectFirst("#synopsis")?.text()
        document.selectFirst("span[data-status]")?.let {
            status = parseStatus(it.text())
        }
        genre = document.select("dl:has(dt:contains(Gênero)) dd a")
            .joinToString { it.text() }

        setUrlWithoutDomain(document.location())
    }

    // ============================== Chapters ===============================

    override val chapterCategory = "Capítulo"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val url = getChapterFeedUrl(document).toHttpUrl().newBuilder()
            .setQueryParameter("start-index", "1")

        val chapters = mutableListOf<SChapter>()
        do {
            val res = client.newCall(GET(url.build(), headers)).execute()

            val page = json.decodeFromString<ZeistMangaDto>(res.body.string()).feed?.entry
                ?.filter { it.category.orEmpty().any { category -> category.term == chapterCategory } }
                ?.map { it.toSChapter(baseUrl) }
                ?: emptyList()

            chapters += page
            url.setQueryParameter("start-index", "${chapters.size + 1}")
        } while (page.isNotEmpty())

        return chapters
    }

    // ============================== Pages ===============================

    override val pageListSelector = "#reader .separator"
}
