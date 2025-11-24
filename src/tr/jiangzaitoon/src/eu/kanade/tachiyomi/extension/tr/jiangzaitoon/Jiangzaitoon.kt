package eu.kanade.tachiyomi.extension.tr.jiangzaitoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Jiangzaitoon : Madara(
    "Jiangzaitoon",
    "https://jiangzaitoon.run",
    "tr",
    SimpleDateFormat("d MMM yyy", Locale("tr")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val client: OkHttpClient by lazy {
        super.client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.MINUTES) // aka shit source
            .build()
    }

    override val chapterUrlSelector = "> a"

    override fun String.getSrcSetImage(): String? {
        /* Assumption: URL is absolute
         * Assumption: descriptor is always in width
         */
        return this
            .split(",")
            .mapNotNull { candidate ->
                candidate
                    .trim()
                    .split(" ", limit = 2)
                    .takeIf { it.size == 2 }
                    ?.let { (url, desc) ->
                        desc
                            .takeIf { it.endsWith("w") }
                            ?.removeSuffix("w")
                            ?.toIntOrNull()
                            ?.let { size -> url to size }
                    }
            }
            .maxByOrNull { it.second }
            ?.first
    }
}
