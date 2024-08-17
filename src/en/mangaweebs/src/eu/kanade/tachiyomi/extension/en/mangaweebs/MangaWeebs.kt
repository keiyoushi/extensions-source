package eu.kanade.tachiyomi.extension.en.mangaweebs

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaWeebs : Madara("Manga Weebs", "https://mangaweebs.in", "en", dateFormat = SimpleDateFormat("dd MMMM HH:mm", Locale.US)) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 4, TimeUnit.SECONDS)
        .build()

    override val mangaDetailsSelectorTag = ""

    override val pageListParseSelector = ".reading-content img:not([src*=\"logo.png\"])"
}
