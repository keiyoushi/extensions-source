package eu.kanade.tachiyomi.extension.en.mangatx

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class MangaTX : MangaThemesia(
    "MangaTX",
    "https://mangatx.cc",
    "en",
    mangaUrlDirectory = "/manga-list",
    dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ROOT),
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val seriesAuthorSelector = ".imptdt:contains(Author) a"
}
