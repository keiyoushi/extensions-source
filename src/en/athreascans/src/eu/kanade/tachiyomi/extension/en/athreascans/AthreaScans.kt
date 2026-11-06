package eu.kanade.tachiyomi.extension.en.athreascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class AthreaScans : MangaThemesia() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    override fun mangaDetailsParse(document: Document) = super.mangaDetailsParse(document).apply {
        // Completed, with chapters still behind coins
        if (status == SManga.COMPLETED && document.selectFirst("#chapterlist .eph-num a:not([href])") != null) {
            status = SManga.PUBLISHING_FINISHED
        }
    }

    override fun chapterListSelector() = "#chapterlist li:has(.eph-num a[href])"
}
