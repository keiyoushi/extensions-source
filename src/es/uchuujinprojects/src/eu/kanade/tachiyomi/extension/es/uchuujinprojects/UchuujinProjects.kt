package eu.kanade.tachiyomi.extension.es.uchuujinprojects

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class UchuujinProjects : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es"))
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val client = super.client.newBuilder()
        .rateLimit(3, 1.seconds) { it.host == baseUrlHost }
        .build()

    override val hasProjectPage = true
}
