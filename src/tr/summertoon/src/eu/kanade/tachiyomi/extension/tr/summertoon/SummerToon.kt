package eu.kanade.tachiyomi.extension.tr.summertoon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.OkHttpClient
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

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        if (document.selectFirst("main.mauve-chapter-gate") != null) {
            throw Exception("Log in via WebView to read.")
        }

        val chapterId = document.postId()!!
        sendView(chapterId)
        if (document.selectFirst("script:containsData(STReaderGateShape=)") == null) {
            return pageListParse(document)
        }

        val nonce = NONCE_REGEX.find(document.selectFirst("script:containsData(STGATE_NONCE)")!!.data())!!.groupValues[1]
        val ajaxUrl = "$baseUrl/wp-admin/admin-ajax.php"

        // Same fields as the site's reader script, with its values for ads that loaded
        val decision = FormBody.Builder()
            .add("action", "stgate_decide")
            .add("nonce", nonce)
            .add("chapter_id", chapterId)
            .add("provider_ok", "1")
            .add("bait_hidden", "0")
            .add("fp_ad_blocked", "0")
            .add("fp_health_ok", "1")
            .add("tp_ad_blocked", "0")
            .add("tp_neutral_ok", "1")
            .add("recheck", "0")
            .add("env", """{"opera":false,"brave":false,"ios":false,"mobile":true,"webview":false}""")
            .build()
        val grant = client.post(ajaxUrl, decision).parseAs<AjaxDto<DecisionDto>>().data.grant

        val manifest = FormBody.Builder()
            .add("action", "stgate_manifest")
            .add("nonce", nonce)
            .add("chapter_id", chapterId)
            .add("grant", grant)
            .build()
        return client.post(ajaxUrl, manifest).parseAs<AjaxDto<ManifestDto>>().data.images
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    companion object {
        private val NONCE_REGEX = """STGATE_NONCE = "(\w+)"""".toRegex()
    }
}
