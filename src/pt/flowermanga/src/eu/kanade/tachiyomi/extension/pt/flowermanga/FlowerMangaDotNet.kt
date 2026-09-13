package eu.kanade.tachiyomi.extension.pt.flowermanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class FlowerMangaDotNet : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)
    override val chapterDateSelector = ".chapter-release-date .timediff"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { !it.encodedPath.startsWith("/wp-content/uploads/") }
}
