package eu.kanade.tachiyomi.extension.tr.jiangzaitoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Jiangzaitoon : Madara(
    "Jiangzaitoon",
    "https://jiangzaitoon.info",
    "tr",
    SimpleDateFormat("d MMM yyy", Locale("tr")),
) {
    override val useNewChapterEndpoint = false

    override val client: OkHttpClient by lazy {
        super.client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.MINUTES) // aka shit source
            .build()
    }

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
