package eu.kanade.tachiyomi.extension.es.nekoscans

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import java.util.concurrent.TimeUnit

class NekoScans : ZeistManga(
    "NekoScans",
    "https://nekoscanlation.blogspot.com",
    "es",
) {
    // Theme changed from MangaThemesia to ZeistManga
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override val popularMangaSelector = "div.PopularPosts.mt-4 div.grid > article"
    override val popularMangaSelectorTitle = "h3 > a"
    override val popularMangaSelectorUrl = "div.item-thumbnail > a"
    override val mangaDetailsSelector = "div.Blog"
    override val mangaDetailsSelectorDescription = "#synopsis > p"
    override val mangaDetailsSelectorGenres = "dl.flex:contains(Genre) > dd > a[rel=tag]"
    override val mangaDetailsSelectorAuthor = "#extra-info dl:contains(Autor) > dd"
    override val mangaDetailsSelectorArtist = "#extra-info dl:contains(Artista) > dd"
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

    override val excludedCategories = listOf("Anime", "Novel")

    override val pageListSelector = "div#readarea img"
}
