package eu.kanade.tachiyomi.extension.all.seraphicdeviltry

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class SeraphicDeviltry : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)
    override val chapterMode = ChapterMode.MangaAjax

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3, 1.seconds) { !it.encodedPath.startsWith("/wp-content/uploads/") }
}
