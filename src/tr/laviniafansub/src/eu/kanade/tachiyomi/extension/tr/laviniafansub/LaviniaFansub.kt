package eu.kanade.tachiyomi.extension.tr.laviniafansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class LaviniaFansub : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    override val chapterMode = ChapterMode.MangaAjax

    override val chapterUrlSelector = "a:not(:has(img))"

    // Each old domain redirects to the next one
    override suspend fun fetchBaseUrls(client: OkHttpClient): List<String> = listOf(
        client.get(baseUrl, Fetch.DOCUMENT).use { it.request.url.toString() },
    )

    override fun parsePages(document: Document): List<Page> {
        val pageList = super.parsePages(document)

        if (
            pageList.isEmpty() &&
            document.select(".content-blocked, .login-required").isNotEmpty()
        ) {
            throw Exception("Okumak için WebView üzerinden giriş yapın")
        }
        return pageList
    }
}
