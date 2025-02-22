package eu.kanade.tachiyomi.extension.pt.safirescan

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import java.util.concurrent.TimeUnit

class SafireScan : ZeistManga(
    "Safire Scan",
    "https://www.safirescan.xyz",
    "pt-BR",
) {
    override val client = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override val popularMangaSelector = "div.PopularPosts.mt-4 div.grid > article"
    override val popularMangaSelectorTitle = "h3 > a"
    override val popularMangaSelectorUrl = "div.item-thumbnail > a"
    override val mangaDetailsSelector = "div.Blog"
    override val mangaDetailsSelectorDescription = "#synopsis > p"
    override val mangaDetailsSelectorGenres = "dl.flex:contains(Gênero) > dd > a[rel=tag]"
    override val mangaDetailsSelectorAuthor = "#extra-info div:contains(Autor)"
    override val mangaDetailsSelectorArtist = "#extra-info div:contains(Artista)"
    override val mangaDetailsSelectorInfo = "span.mr-2.rounded"

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val blog = document.selectFirst(mangaDetailsSelector)!!
        return SManga.create().apply {
            thumbnail_url = blog.selectFirst("header div.grid > img")!!.attr("abs:src")
            description = blog.selectFirst(mangaDetailsSelectorDescription)!!.text()
            genre = blog.select(mangaDetailsSelectorGenres)
                .joinToString { it.text() }
            author = blog.selectFirst(mangaDetailsSelectorAuthor)?.text()
            artist = blog.selectFirst(mangaDetailsSelectorArtist)?.text()
            status = parseStatus(blog.selectFirst(mangaDetailsSelectorInfo)!!.text())
        }
    }

    override val pageListSelector = "div.separator img"
}
