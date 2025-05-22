package eu.kanade.tachiyomi.extension.tr.hyperionscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class HyperionScans : MangaThemesia(
    "Seraph Manga",
    "https://www.seraphmanga.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),
) {
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
