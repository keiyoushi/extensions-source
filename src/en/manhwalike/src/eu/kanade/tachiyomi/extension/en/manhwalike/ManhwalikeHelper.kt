package eu.kanade.tachiyomi.extension.en.manhwalike

import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.RequestBody
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object ManhwalikeHelper {
    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("America/New_York") }

    fun Headers.Builder.buildApiHeaders(requestBody: RequestBody) = this
        .add("Content-Length", requestBody.contentLength().toString())
        .add("Content-Type", requestBody.contentType().toString())
        .add("Accept", "text/html")
        .add("X-Requested-With", "XMLHttpRequest")
        .build()

    inline fun <reified T : Any> T.toFormRequestBody(): RequestBody {
        return FormBody.Builder()
            .add("keyword", this.toString())
            .build()
    }

    fun String?.toStatus(): Int {
        return when {
            this == null -> SManga.UNKNOWN
            this.contains("Ongoing", true) -> SManga.ONGOING
            this.contains("Finish", true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    fun String?.toDate(): Long {
        return try {
            dateFormat.parse(this).time
        } catch (_: Exception) {
            0L
        }
    }

    fun Element.toOriginal(): String = when {
        hasAttr("data-original") -> absUrl("data-original")
        else -> absUrl("src")
    }
}
