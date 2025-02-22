package eu.kanade.tachiyomi.extension.en.manhuaga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient

class Manhuaga : Madara("Manhuaga", "https://manhua-ga.org", "en") {
    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            chain.proceed(originalRequest).let { response ->
                if (response.code == 403) {
                    response.close()
                    chain.proceed(originalRequest.newBuilder().removeHeader("Referer").addHeader("Referer", baseUrl).build())
                } else {
                    response
                }
            }
        }
        .build()
}
