package eu.kanade.tachiyomi.extension.tr.moondaisyscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr")).apply {
    timeZone = TimeZone.getTimeZone("Europe/Istanbul")
}

@Source
abstract class MoonDaisyScans : MangaThemesia() {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
