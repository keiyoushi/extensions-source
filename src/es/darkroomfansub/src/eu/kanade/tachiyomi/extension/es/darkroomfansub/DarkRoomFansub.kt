package eu.kanade.tachiyomi.extension.es.darkroomfansub

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.Response

@Source
abstract class DarkRoomFansub : ZeistManga() {

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val mangaDetailsSelector = "#main"

    override val pageListSelector = "article#reader div.separator"
    override val mangaDetailsSelectorDescription = "#synopsis"
    override val mangaDetailsSelectorGenres = "a[rel=tag]"

    override val mangaDetailsSelectorInfoTitle = "dt"
    override val mangaDetailsSelectorInfoDescription = "dd"
    override val mangaDetailsSelectorInfo = "#extra-info > dl"

    override val supportsLatest = false
    override fun popularMangaRequest(page: Int) = latestUpdatesRequest(page)
    override fun popularMangaParse(response: Response) = latestUpdatesParse(response)
}
