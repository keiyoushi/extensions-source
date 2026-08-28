package eu.kanade.tachiyomi.extension.es.esmi2manga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class EsMi2Manga : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale("es"))
    override fun OkHttpClient.Builder.configureClient() = rateLimit(2, 1.seconds) { !it.encodedPath.startsWith("/wp-content/uploads/") }
}
