package eu.kanade.tachiyomi.extension.es.kokkokuteiscankt

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class KokkokuteiScanKt : ZeistManga() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3) { it.host == baseUrl.toHttpUrl().host }

    // Popular already lists the series newest first: the home page's popular widget has only five
    override val supportsLatest = false

    // Light novels, whose chapters are text
    override val excludedCategories get() = super.excludedCategories + "LN"

    override val mangaDetailsSelector = "#main"
    override val mangaDetailsSelectorGenres = "a[rel=tag]"
    override val mangaDetailsSelectorInfo = "#extra-info > dl"
    override val mangaDetailsSelectorInfoTitle = "dt"
    override val mangaDetailsSelectorInfoDescription = "dd"

    override val pageListSelector = "article#reader div.separator"
}
