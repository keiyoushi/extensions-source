package eu.kanade.tachiyomi.extension.es.nartag

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class Nartag :
    Madara(
        "Traducciones Amistosas",
        "https://nartag.com",
        "es",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
    ) {
    override val versionId = 2

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    // =============================== Pages ===============================

    override val pageListParseSelector = "div.page-break, li.blocks-gallery-item, .reading-content .text-left:not(:has(.blocks-gallery-item)) img, div.rk-page-wrap"

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val redirectForm = document.selectFirst("form#rk_madara_redirect")

        if (redirectForm != null) {
            val actionUrl = redirectForm.attr("abs:action")
            if (actionUrl.isNotBlank()) {
                val formBody = FormBody.Builder().apply {
                    redirectForm.select("input[name]").forEach {
                        add(it.attr("name"), it.attr("value"))
                    }
                }.build()

                val postRequest = Request.Builder()
                    .url(actionUrl)
                    .post(formBody)
                    .headers(response.request.headers)
                    .build()

                client.newCall(postRequest).execute().use { newResponse ->
                    return pageListParse(newResponse.asJsoup())
                }
            }
        }

        return pageListParse(document)
    }
}
