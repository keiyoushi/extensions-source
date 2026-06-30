package eu.kanade.tachiyomi.extension.en.kissmangain

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

@Source
abstract class KissmangaIn : Madara() {
    override val mangaSubString = "kissmanga"

    override val useNewChapterEndpoint = true

    // ============================== Popular ==============================

    override fun popularMangaFromElement(element: Element): SManga = super.popularMangaFromElement(element).stripQueryParams()

    // =============================== Latest ==============================

    override fun latestUpdatesFromElement(element: Element): SManga = super.latestUpdatesFromElement(element).stripQueryParams()

    // ============================== Search ===============================

    override fun searchMangaFromElement(element: Element): SManga = super.searchMangaFromElement(element).stripQueryParams()

    // ============================== Details ==============================

    override fun relatedMangaListParse(response: Response): List<SManga> = super.relatedMangaListParse(response).map { it.stripQueryParams() }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url.substringBefore("?"), headers)

    // ============================= Chapters ==============================

    override fun xhrChaptersRequest(mangaUrl: String): Request = super.xhrChaptersRequest(mangaUrl.substringBefore("?").removeSuffix("/"))

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        url = url.substringBefore("?") + "?style=list"
    }

    // =============================== Pages ===============================

    override fun imageFromElement(element: Element): String? = super.imageFromElement(element)?.trim()

    // ============================= Utilities =============================

    private fun SManga.stripQueryParams(): SManga = apply {
        url = url.substringBefore("?")
    }
}
