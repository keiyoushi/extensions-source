package eu.kanade.tachiyomi.extension.en.topmanhua

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TopManhua : Madara() {
    override val chapterMode = ChapterMode.MangaAjaxPaginated
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MM/dd/yy", Locale.US)

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) {
        !it.encodedPath.startsWith("/wp-content/uploads/")
    }

    // The website does not flag the content.
    override val filterNonMangaItems = false

    override val mangaSubString = "series"
}
