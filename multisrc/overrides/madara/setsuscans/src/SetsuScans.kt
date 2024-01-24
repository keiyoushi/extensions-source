package eu.kanade.tachiyomi.extension.en.setsuscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Response

class SetsuScans : Madara(
    "Setsu Scans",
    "https://setsuscans.com",
    "en",
) {
    override val client = super.client.newBuilder()
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            if (url.host == "i0.wp.com") {
                val newUrl = url.newBuilder()
                    .removeAllQueryParameters("fit")
                    .removeAllQueryParameters("ssl")
                    .build()

                return@addNetworkInterceptor chain.proceed(
                    request.newBuilder()
                        .url(newUrl)
                        .build(),
                )
            }

            return@addNetworkInterceptor chain.proceed(request)
        }
        .rateLimit(2)
        .build()

    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String {
        return if (page > 1) {
            "page/$page/"
        } else {
            ""
        }
    }

    override fun popularMangaParse(response: Response) =
        super.popularMangaParse(response).fixNextPage()

    override fun latestUpdatesParse(response: Response) =
        super.latestUpdatesParse(response).fixNextPage()

    override fun searchMangaParse(response: Response) =
        super.searchMangaParse(response).fixNextPage()

    private fun MangasPage.fixNextPage(): MangasPage {
        return if (mangas.size < 12) {
            MangasPage(mangas, false)
        } else {
            this
        }
    }

    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(status) + div.summary-content"
}
