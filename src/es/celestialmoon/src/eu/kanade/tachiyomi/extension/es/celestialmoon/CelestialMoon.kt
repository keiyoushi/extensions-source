package eu.kanade.tachiyomi.extension.es.celestialmoon

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import java.util.concurrent.TimeUnit

class CelestialMoon : ZeistManga(
    "Celestial Moon",
    "https://www.celestialmoonscan.com",
    "es",
) {
    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 3, 1, TimeUnit.SECONDS)
        .build()

    override val popularMangaSelector = "div.PopularPosts > div.grid > article"
    override val popularMangaSelectorUrl = ".post-title > a"
    override val popularMangaSelectorTitle = ".post-title"

    override val mangaDetailsSelector = "div.Blog > main"
    override val mangaDetailsSelectorGenres = "dl:has(dt:contains(Genre)) dd a[rel=tag]"
    override val mangaDetailsSelectorAuthor = "div#extra-info > dl:has(dt:contains(Author)) dd"
    override val mangaDetailsSelectorArtist = "div#extra-info > dl:has(dt:contains(Artist)) dd"

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val profileManga = document.selectFirst(mangaDetailsSelector)!!
        return SManga.create().apply {
            thumbnail_url = profileManga.selectFirst("img")!!.attr("abs:src")
            description = profileManga.select(mangaDetailsSelectorDescription).text()
            genre = profileManga.select(mangaDetailsSelectorGenres)
                .joinToString { it.text() }
            author = profileManga.selectFirst(mangaDetailsSelectorAuthor)?.text()
            artist = profileManga.selectFirst(mangaDetailsSelectorArtist)?.text()
            status = parseStatus(document.selectFirst("main > header > div.grid span[data-status]")!!.text())
            title = profileManga.selectFirst("main > header > div.grid h1[itemprop=name]")!!.text()
        }
    }

    override val pageListSelector = "article.chapter > div.separator"

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val images = document.select(pageListSelector)
        return images.select("> a").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("abs:href"))
        }
    }
}
