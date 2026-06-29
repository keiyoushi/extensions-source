package eu.kanade.tachiyomi.extension.en.rdscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Response
import org.jsoup.nodes.Document

class RDScans :
    Madara(
        "RD Scans",
        "https://rdscans.com",
        "en",
    ) {
    override val mangaSubString = "new"
    override val mangaEntrySelector = ""
    override val useNewChapterEndpoint = true

    private fun parseBrowsePage(mangasPage: MangasPage): MangasPage {
        // Normally returns a 200 OK with a challenge page which leads to a "No results found" error for the user.
        // This check throws an exception instead, prompting the user to view in WebView first.
        if (mangasPage.mangas.isEmpty()) {
            throw Exception("Failed to bypass Cloudflare")
        }
        return filterWebNovels(mangasPage)
    }

    private fun filterWebNovels(mangasPage: MangasPage): MangasPage {
        val filteredMangas = mangasPage.mangas.filterNot {
            it.title.contains("(WN)", ignoreCase = true)
        }
        return MangasPage(filteredMangas, mangasPage.hasNextPage)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseBrowsePage(super.popularMangaParse(response))

    override fun latestUpdatesParse(response: Response): MangasPage = parseBrowsePage(super.latestUpdatesParse(response))

    override fun searchMangaParse(response: Response): MangasPage = filterWebNovels(super.searchMangaParse(response))

    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }
        return document.select("div.reading-content .separator img").mapIndexed { i, img ->
            Page(i, document.location(), imageFromElement(img))
        }
    }
}
