package eu.kanade.tachiyomi.extension.tr.moondaisyscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr")).apply {
    timeZone = TimeZone.getTimeZone("Europe/Istanbul")
}

class MoonDaisyScans :
    MangaThemesia(
        "Moon Daisy Scans",
        "https://moondaisyscans.pro",
        "tr",
        dateFormat = dateFormat,
    ) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
