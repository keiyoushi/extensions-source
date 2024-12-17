package eu.kanade.tachiyomi.extension.id.sektedoujin

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class SekteDoujin : MangaThemesia("Sekte Doujin", "https://sektedoujin.cc", "id", dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id"))) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()
}
