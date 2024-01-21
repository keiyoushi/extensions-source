package eu.kanade.tachiyomi.extension.tr.tempestfansub

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl

class TempestFansub : MangaThemesia(
    "Tempest Fansub",
    "https://tempestfansub.com",
    "tr",
) {
    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    // =========================== Manga Details ============================
    override val seriesArtistSelector = ".tsinfo .imptdt:contains(İllüstratör) i"
    override val seriesAuthorSelector = ".tsinfo .imptdt:contains(Yazar) i"
}
