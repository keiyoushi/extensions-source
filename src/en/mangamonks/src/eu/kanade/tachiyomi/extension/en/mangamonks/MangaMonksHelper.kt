package eu.kanade.tachiyomi.extension.en.mangamonks

import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.RequestBody
import java.util.Calendar

object MangaMonksHelper {
    fun Headers.Builder.buildApiHeaders(requestBody: RequestBody) = this
        .add("Content-Length", requestBody.contentLength().toString())
        .add("Content-Type", requestBody.contentType().toString())
        .add("Accept", "application/json")
        .add("X-Requested-With", "XMLHttpRequest")
        .build()
    inline fun <reified T : Any> T.toFormRequestBody(): RequestBody {
        return FormBody.Builder()
            .add("dataType", "json")
            .add("phrase", this.toString())
            .build()
    }
    fun String?.toStatus(): Int {
        return when {
            this == null -> SManga.UNKNOWN
            this.contains("Ongoing", true) -> SManga.ONGOING
            this.contains("Completed", true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
    fun String?.toDate(): Long {
        val trimmedDate = this!!.substringBefore(" ago").removeSuffix("s").split(" ")
        val calendar = Calendar.getInstance()

        when {
            trimmedDate[1].contains(
                "Year",
                ignoreCase = true,
            ) -> calendar.apply { add(Calendar.YEAR, -trimmedDate[0].toInt()) }

            trimmedDate[1].contains(
                "Month",
                ignoreCase = true,
            ) -> calendar.apply { add(Calendar.MONTH, -trimmedDate[0].toInt()) }

            trimmedDate[1].contains(
                "Week",
                ignoreCase = true,
            ) -> calendar.apply { add(Calendar.WEEK_OF_MONTH, -trimmedDate[0].toInt()) }

            trimmedDate[1].contains(
                "Day",
                ignoreCase = true,
            ) -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }

            trimmedDate[1].contains(
                "Hour",
                ignoreCase = true,
            ) -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }

            trimmedDate[1].contains(
                "Minute",
                ignoreCase = true,
            ) -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }
        }

        return calendar.timeInMillis
    }
}
