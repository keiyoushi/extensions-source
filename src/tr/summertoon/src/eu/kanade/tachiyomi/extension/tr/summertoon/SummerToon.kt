package eu.kanade.tachiyomi.extension.tr.summertoon
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class SummerToon : MangaThemesia(
    "SummerToon",
    "https://summertoon.co",
    "tr",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(1, 1)
        .build()

    override val seriesStatusSelector = ".tsinfo .imptdt:contains(Durum) i"
    override val seriesAuthorSelector = ".fmed b:contains(Yazar)+span"
}
