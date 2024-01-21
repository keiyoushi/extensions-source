package eu.kanade.tachiyomi.extension.tr.tempestfansub

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.SManga
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
    override val seriesStatusSelector = ".tsinfo .imptdt:contains(Seri Durumu) i"

    override fun String?.parseStatus(): Int = when (this?.trim()?.lowercase()) {
        "devam ediyor" -> SManga.ONGOING
        "bitti" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}
