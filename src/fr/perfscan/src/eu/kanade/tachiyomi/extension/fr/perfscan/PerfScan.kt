package eu.kanade.tachiyomi.extension.fr.perfscan

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

class PerfScan : HeanCms("Perf Scan", "https://perf-scan.fr", "fr") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimitHost(apiUrl.toHttpUrl(), 1, 2)
        .build()

    override val slugStrategy = SlugStrategy.ID

    override fun pageListParse(response: Response): List<Page> {
        val paidChapter = response.request.url.fragment?.contains("-paid")

        val document = response.asJsoup()

        val scriptsData = document.select("script").joinToString("\n") { it.data() }

        val imagesData = IMAGES_URL_REGEX.find(scriptsData)?.groupValues?.get(1)

        val images = imagesData?.split(",")?.mapIndexed { i, img ->
            Page(i, imageUrl = img.replace("\\\"", ""))
        } ?: emptyList()

        if (images.isEmpty() && paidChapter == true) {
            throw Exception(intl.paidChapterError)
        }

        return images
    }

    companion object {
        private val IMAGES_URL_REGEX = """\{\\"data\\":\[(.*?)\]""".toRegex()
    }
}
