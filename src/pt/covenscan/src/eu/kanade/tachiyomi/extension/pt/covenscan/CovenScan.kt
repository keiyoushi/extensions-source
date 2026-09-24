package eu.kanade.tachiyomi.extension.pt.covenscan

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class CovenScan : MadaraNoAjax() {
    override val supportsPostId = false
    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 2.seconds)
    override val chapterMode = ChapterMode.MangaAjax

    override val mangaDetailsSelectorAuthor = "div.post-content_item:contains(Author) > div.summary-content"
    override val supportsFilterFetching = false
}
