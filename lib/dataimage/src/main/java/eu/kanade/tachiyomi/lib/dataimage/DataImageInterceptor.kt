package eu.kanade.tachiyomi.lib.dataimage

import android.util.Base64
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Element

/**
 * If a source provides images via a data:image string instead of a URL, use these functions and interceptor
 */

/**
 * Use if the attribute tag could have a data:image string or URL
 * Transforms data:image in to a fake URL that OkHttp won't die on
 */
fun Element.dataImageAsUrl(attr: String): String {
    return if (this.attr(attr).startsWith("data")) {
        "https://127.0.0.1/?" + this.attr(attr).substringAfter(":")
    } else {
        this.attr("abs:$attr")
    }
}

/**
 * Use if the attribute tag has a data:image string but real URLs are on a different attribute
 */
fun Element.dataImageAsUrlOrNull(attr: String): String? {
    return if (this.attr(attr).startsWith("data")) {
        "https://127.0.0.1/?" + this.attr(attr).substringAfter(":")
    } else {
        null
    }
}

/**
 * Interceptor that detects the URLs we created with the above functions, base64 decodes the data if necessary,
 * and builds a response with a valid image that Tachiyomi can display
 */
class DataImageInterceptor : Interceptor {
    private val mediaTypePattern = Regex("""(^[^;,]*)[;,]""")

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url.toString()
        return if (url.startsWith("https://127.0.0.1/?image")) {
            val dataString = url.substringAfter("?")
            val byteArray = if (dataString.contains("base64")) {
                Base64.decode(dataString.substringAfter("base64,"), Base64.DEFAULT)
            } else {
                dataString.substringAfter(",").toByteArray()
            }
            val mediaType = mediaTypePattern.find(dataString)!!.value.toMediaTypeOrNull()
            Response.Builder().body(byteArray.toResponseBody(mediaType))
                .request(chain.request())
                .protocol(Protocol.HTTP_1_0)
                .code(200)
                .message("")
                .build()
        } else {
            chain.proceed(chain.request())
        }
    }
}
