package eu.kanade.tachiyomi.extension.en.lilymanga

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LilyManga : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.US)
    override val genreDirectory = "gl-genre"
    override val mangaSubString = "gl"
    override val chapterMode = ChapterMode.MangaAjax

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 2.seconds) {
        it.host == baseUrl.toHttpUrl().host
    }
}
