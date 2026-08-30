package eu.kanade.tachiyomi.extension.es.darkroomfansub

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class DarkRoomFansub : ZeistManga() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    override val supportsLatest = false

    override val mangaDetailsSelector = "#main"

    override val pageListSelector = "article#reader div.separator"
    override val mangaDetailsSelectorDescription = "#synopsis"
    override val mangaDetailsSelectorGenres = "a[rel=tag]"

    override val mangaDetailsSelectorInfoTitle = "dt"
    override val mangaDetailsSelectorInfoDescription = "dd"
    override val mangaDetailsSelectorInfo = "#extra-info > dl"
}
