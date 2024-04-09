package eu.kanade.tachiyomi.extension.fr.perfscan

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class PerfScan : HeanCms("Perf Scan", "https://perf-scan.fr", "fr") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimitHost(apiUrl.toHttpUrl(), 1, 2)
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
