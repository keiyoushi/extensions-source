package eu.kanade.tachiyomi.extension.en.manhwalike
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.RequestBody
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object ManhwalikeHelper {
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
        val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
        val date = this?.let { dateFormat.parse(it) }
        val calendar = Calendar.getInstance().apply { time = date }
        return calendar.timeInMillis
    }
}
