package eu.kanade.tachiyomi.extension.es.mangaesp

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaEsp : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("en"))
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val client = super.client.newBuilder()
        .rateLimit(3, 1.seconds) { it.host == baseUrlHost }
        .build()

    override val hasProjectPage = true

    override val projectPageString = "/proyectos"
}
