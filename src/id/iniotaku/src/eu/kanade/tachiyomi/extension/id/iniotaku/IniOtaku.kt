package eu.kanade.tachiyomi.extension.id.iniotaku

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class IniOtaku : ZeistManga() {

    override val popularMangaSelector = "div.PopularPosts div.grid > article"
    override val popularMangaSelectorTitle = "h3 > a"
    override val popularMangaSelectorUrl = "h3 > a"

    // Chapter posts don't link to their series post, so only series links resolve.
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val title = document.selectFirst("main h1[itemprop=name]")?.text() ?: return null
        return mangaDetailsParse(document).apply {
            this.url = url.encodedPath
            this.title = title
        }
    }

    override val mangaDetailsSelector = "main"
    override val mangaDetailsSelectorGenres = "aside dl a[rel=tag]"
    override val mangaDetailsSelectorInfo = "#extra-info dl"
    override val mangaDetailsSelectorInfoTitle = "dt"
    override val mangaDetailsSelectorInfoDescription = "dd"

    override val pageListSelector = "#reader"
}
