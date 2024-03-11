package eu.kanade.tachiyomi.extension.es.territoriolealtad

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class TerritorioLealtad : Madara(
    "Territorio Lealtad",
    "https://territorioleal.com",
    "es",
    SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()
}
