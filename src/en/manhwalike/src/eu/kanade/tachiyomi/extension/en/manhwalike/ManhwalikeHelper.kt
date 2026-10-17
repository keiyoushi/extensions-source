package eu.kanade.tachiyomi.extension.en.manhwalike

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDate
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.RequestBody
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ManhwalikeHelper {
    private val dateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
    private val dateZone = ZoneId.of("America/New_York")

    fun Headers.Builder.buildApiHeaders(requestBody: RequestBody) = this
        .add("Content-Length", requestBody.contentLength().toString())
        .add("Content-Type", requestBody.contentType().toString())
        .add("Accept", "text/html")
        .add("X-Requested-With", "XMLHttpRequest")
        .build()

    inline fun <reified T : Any> T.toFormRequestBody(): RequestBody = FormBody.Builder()
        .add("keyword", this.toString())
        .build()

    fun String?.toStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        this.contains("Ongoing", true) -> SManga.ONGOING
        this.contains("Finish", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    fun String?.toDate(): Long = dateFormat.tryParseDate(this, dateZone)

    fun Element.toOriginal(): String = when {
        hasAttr("data-original") -> absUrl("data-original")
        else -> absUrl("src")
    }
}
