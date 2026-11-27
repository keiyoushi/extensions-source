package eu.kanade.tachiyomi.extension.tr.laviniafansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import okhttp3.OkHttpClient
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
}
