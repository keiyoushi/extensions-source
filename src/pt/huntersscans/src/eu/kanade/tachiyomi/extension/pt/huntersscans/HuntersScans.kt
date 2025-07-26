package eu.kanade.tachiyomi.extension.pt.huntersscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class HuntersScans : Madara(
    "Hunters Scan",
    "https://readhunters.xyz",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(1, 2)
        .readTimeout(3, TimeUnit.MINUTES)
        .build()

    override val mangaSubString = "comics"

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        launchIO { countViews(document) }

        val mangaUrl = document.location().removeSuffix("/")
        var page = 1
        val chapters = mutableListOf<SChapter>()
        do {
            val url = xhrChaptersRequest(mangaUrl).url.newBuilder()
                .addQueryParameter("t", "${page++}")
                .build()

            val request = xhrChaptersRequest(mangaUrl).newBuilder()
                .url(url)
                .build()

            val xhrResponse = client.newCall(request)
                .execute()
                .asJsoup()

            val currentPage = xhrResponse.select(chapterListSelector()).map(::chapterFromElement)
            chapters += currentPage
        } while (currentPage.isNotEmpty())
        return chapters
    }
}
