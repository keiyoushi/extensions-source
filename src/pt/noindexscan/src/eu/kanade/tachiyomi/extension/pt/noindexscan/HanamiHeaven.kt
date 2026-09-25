package eu.kanade.tachiyomi.extension.pt.noindexscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class HanamiHeaven : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    override fun OkHttpClient.Builder.configureClient() = rateLimit(3, 2.seconds)

    override val mangaDetailsSelectorTitle = "div.post-title-test h1, div.post-title h1"
    override val mangaDetailsSelectorStatus = "div.summary-heading:has(h5:contains(Status)) + div.summary-content"

    override val chapterMode = ChapterMode.MangaAjax
}
