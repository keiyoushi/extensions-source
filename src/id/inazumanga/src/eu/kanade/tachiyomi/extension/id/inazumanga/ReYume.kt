package eu.kanade.tachiyomi.extension.id.inazumanga

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ReYume : ZeistManga("ReYume", "https://www.re-yume.my.id", "id") {

    override val popularMangaSelector = "#Side div.group"
    override val popularMangaSelectorTitle = "h3"
    override val popularMangaSelectorUrl = "a"

    override val mangaDetailsSelector = "#main"
    override val pageListSelector = "div.i_img"

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector).map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("a").getStyleUrl()
                title = element.selectFirst(popularMangaSelectorTitle)!!.text()
                setUrlWithoutDomain(element.selectFirst(popularMangaSelectorUrl)!!.absUrl("href"))
            }
        }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val profileManga = document.selectFirst(mangaDetailsSelector)!!
        return SManga.create().apply {
            thumbnail_url = profileManga.selectFirst("div.thum").getStyleUrl()
            title = profileManga.selectFirst("#post-title")!!.text()
            description = profileManga.selectFirst("#syn_bod")?.text()?.trim()
            genre = profileManga.select("a[rel=tag]").joinToString { it.text() }
            author = profileManga.selectFirst("span#tauther")?.text()?.trim()
            artist = profileManga.selectFirst("span#tartist")?.text()?.trim()

            profileManga.selectFirst("span#talternative")?.text()?.takeIf { it.isNotBlank() }?.let {
                description = listOfNotNull(description?.takeIf { it.isNotBlank() }, "Alternative title(s): $it").joinToString("\n\n")
            }

            val statusElement = profileManga.select(".capitalize").firstOrNull {
                val text = it.text().lowercase().trim()
                text in statusOnGoingList || text in statusCompletedList || text in statusHiatusList || text in statusCancelledList
            }
            status = parseStatus(statusElement?.text() ?: "Unknown")
        }
    }

    override fun getChapterFeedUrl(doc: Document): String {
        val label = doc.selectFirst(".chapter_get")?.attr("data-labelchapter")
            ?: throw Exception("Failed to find chapter feed label")
        return apiUrl(label).build().toString()
    }

    private fun Element?.getStyleUrl(): String? {
        val style = this?.attr("style") ?: return null
        return STYLE_URL_REGEX.find(style)?.groupValues?.get(1)
    }

    companion object {
        private val STYLE_URL_REGEX = """url\(['"]*(.*?)['"]*\)""".toRegex()
    }
}
