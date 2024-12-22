package eu.kanade.tachiyomi.extension.tr.mangakings

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

class MangaKings : MangaThemesia(
    "Manga Kings",
    "https://mangakings.com.tr",
    "tr",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),

) {
    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    // =========================== Manga Details ============================
    override val seriesArtistSelector = ".fmed b:contains(Çizer) + span"
    override val seriesAuthorSelector = ".fmed b:contains(Yazar) + span"
    override val seriesStatusSelector = ".tsinfo .imptdt:contains(Durum) i"
    override val seriesTypeSelector = ".tsinfo .imptdt:contains(Türü) a"

    override fun String?.parseStatus() = when {
        this == null -> SManga.UNKNOWN
        contains("Devam Ediyor", true) -> SManga.ONGOING
        contains("Tamamlandı", true) || contains("bitti", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}
