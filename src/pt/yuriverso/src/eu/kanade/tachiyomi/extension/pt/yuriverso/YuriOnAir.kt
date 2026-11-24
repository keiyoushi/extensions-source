package eu.kanade.tachiyomi.extension.pt.yuriverso

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class YuriOnAir : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))
    override val chapterMode = ChapterMode.MangaAjax
    override val genreDirectory = "manga-genero"
    override val archiveUrlSelector = ".post-title a:not(:has(.manga-title-badges))"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 2.seconds) { !it.encodedPath.startsWith("/wp-content/uploads/") }
}
