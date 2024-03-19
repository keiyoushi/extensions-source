package eu.kanade.tachiyomi.extension.fr.perfscan

import android.app.Application
import android.content.SharedPreferences
import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PerfScan : HeanCms("Perf Scan", "https://perf-scan.fr", "fr") {

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

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

    override val useNewChapterEndpoint = true
}
