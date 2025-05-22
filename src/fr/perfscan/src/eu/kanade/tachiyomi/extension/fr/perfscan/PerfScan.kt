package eu.kanade.tachiyomi.extension.fr.perfscan

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

class PerfScan : HeanCms("Perf Scan", "https://perf-scan.fr", "fr") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(apiUrl.toHttpUrl(), 1, 2.seconds)
        .build()

    init {
        preferences.run {
            if (contains("pref_url_map")) {
                edit().remove("pref_url_map").apply()
            }
        }
    }

    override val useNewQueryEndpoint = true
    override val useNewChapterEndpoint = true
}
