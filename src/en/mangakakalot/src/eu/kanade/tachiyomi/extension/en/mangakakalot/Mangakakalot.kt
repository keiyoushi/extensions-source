package eu.kanade.tachiyomi.extension.en.mangakakalot

import eu.kanade.tachiyomi.multisrc.mangabox.MangaBox
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Headers
import okhttp3.Request

class Mangakakalot : MangaBox("Mangakakalot", "https://www.mangakakalot.gg", "en") {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder().set("Referer", "https://www.mangakakalot.gg/") // for covers
    override val popularUrlPath = "manga-list/hot-manga?page="
    override val latestUrlPath = "manga-list/latest-manga?page="
    override val simpleQueryPath = "search/story/"
    override fun searchMangaSelector() = "${super.searchMangaSelector()}, div.list-truyen-item-wrap"

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headersBuilder().build())
    }
}
