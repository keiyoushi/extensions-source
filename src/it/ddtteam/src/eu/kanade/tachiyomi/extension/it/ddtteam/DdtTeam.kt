package eu.kanade.tachiyomi.extension.it.ddtteam

import eu.kanade.tachiyomi.multisrc.pizzareader.PizzaReader
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class DdtTeam : PizzaReader() {
    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
            val url = chain.request().url.newBuilder()
                .scheme("https")
                .build()

            val request = chain.request().newBuilder()
                .url(url)
                .build()

            chain.proceed(request)
        }
        rateLimit(1)
    }
}
