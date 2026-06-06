package eu.kanade.tachiyomi.extension.all.comicfury

import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup

class ContentWarningInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.isSuccessful && response.header("Content-Type")?.contains("text/html") == true) {
            val body = response.body ?: return response
            val bodyString = body.string()

            val document = Jsoup.parse(bodyString, request.url.toString())
            if (document.title().contains("Content Warning") && document.selectFirst("input[name=proceed][value=View Webcomic]") != null) {
                val token = document.selectFirst("input[name=token]")?.attr("value")
                if (token != null) {
                    response.close()
                    val formBody = FormBody.Builder()
                        .add("token", token)
                        .add("proceed", "View Webcomic")
                        .build()

                    val postRequest = request.newBuilder()
                        .post(formBody)
                        .build()

                    return chain.proceed(postRequest)
                }
            }

            val contentType = body.contentType()
            val newBody = bodyString.toResponseBody(contentType)
            return response.newBuilder().body(newBody).build()
        }

        return response
    }
}
