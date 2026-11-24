package eu.kanade.tachiyomi.extension.tr.kazescans

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.utils.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document

@Source
abstract class KazeScans : ZeistManga() {

    override val mangaDetailsSelector = ".series-container"
    override val mangaDetailsSelectorThumbnail = ".series-cover img"
    override val mangaDetailsSelectorDescription = ".series-summary p"
    override val mangaDetailsSelectorGenres = ".series-tags .series-tag"

    override val chapterCategory = "Bölüm"
    override val chapterFeedRegex = """loadChapterGrid\(["'](.*?)["']\)""".toRegex()
    override val scriptSelector = "script:containsData(loadChapterGrid)"

    override val statusOnGoingList = super.statusOnGoingList + listOf("devam ediyor")
    override val statusCompletedList = super.statusCompletedList + listOf("tamamlandı", "tamamlandi")

    override fun parsePopularManga(response: Response): MangasPage {
        val mangas = response.asJsoup().select("div.PopularPosts a.pick-card").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                title = element.selectFirst(".pick-title")?.text().orEmpty()
                setUrlWithoutDomain(element.attr("abs:href"))
            }
        }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = super.mangaDetailsParse(document)
        document.select(".meta-item").forEach { item ->
            val label = item.selectFirst(".meta-label")?.text().orEmpty()
            val value = item.selectFirst(".meta-value")?.text().orEmpty()
            when {
                label.contains("Yazar", ignoreCase = true) -> {
                    manga.author = value
                    manga.artist = value
                }
                label.contains("Durum", ignoreCase = true) -> {
                    manga.status = parseStatus(value)
                }
            }
        }
        return manga
    }

    override fun pageListParse(document: Document): List<Page> = document.select("div.reader-content img[src]")
        .map { it.attr("abs:src") }
        .filterNot { it.contains("on-izleme") }
        .mapIndexed { i, url -> Page(i, "", url) }
}
