package eu.kanade.tachiyomi.extension.pt.ninjascan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

@Source
abstract class NinjaScan : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
    override fun OkHttpClient.Builder.configureClient() = connectTimeout(5.minutes)
        .readTimeout(5.minutes)
        .rateLimit(2) { !it.encodedPath.startsWith("/wp-content/uploads/") }
}
