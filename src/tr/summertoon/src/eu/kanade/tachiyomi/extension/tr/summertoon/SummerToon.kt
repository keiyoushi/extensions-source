package eu.kanade.tachiyomi.extension.tr.summertoon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.tryParse
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class SummerToon : MangaThemesia() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 1.seconds)

    override val statusOptions = arrayOf(
        intl["status_filter_option_all"] to "",
        "Devam Ediyor" to "ongoing",
        "Güncel" to "current",
        "Tamamlandı" to "completed",
        "Sezon Finali" to "season_final",
        "Bırakıldı" to "dropped",
    )

    // The value comes after an icon that is also an <i>
    override val seriesStatusSelector = ".tsinfo .imptdt:contains(Durum) > i"

    override fun String?.parseStatus() = when (this?.lowercase()) {
        "devam ediyor", "güncel" -> SManga.ONGOING
        "tamamlandı" -> SManga.COMPLETED
        "sezon finali" -> SManga.ON_HIATUS
        "bırakıldı" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    override fun chapterFromElement(element: Element) = super.chapterFromElement(element).apply {
        date_upload = Instant.tryParse(element.selectFirst("time.chapterdate")?.attr("datetime"))
    }

    // Members-only chapters show a sign-in prompt in place of the pages
    override fun pageListParse(document: Document): List<Page> {
        if (document.selectFirst("#readerarea .wmcp-paywall-wrapper") != null) return emptyList()
        return super.pageListParse(document)
    }
}
