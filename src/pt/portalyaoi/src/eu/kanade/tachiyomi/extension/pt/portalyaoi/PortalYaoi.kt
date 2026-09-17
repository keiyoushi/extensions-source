package eu.kanade.tachiyomi.extension.pt.portalyaoi

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class PortalYaoi : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("pt", "BR"))

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 2.seconds) {
        !it.encodedPath.startsWith("/wp-content/uploads/")
    }

    override fun imageFromElement(element: Element): String? = when {
        element.hasAttr("data-obf") -> decodePageUrl(element.attr("data-obf"))
        else -> super.imageFromElement(element)
    }

    private fun decodePageUrl(value: String): String = String(Base64.decode(value.reversed(), Base64.DEFAULT))
}
