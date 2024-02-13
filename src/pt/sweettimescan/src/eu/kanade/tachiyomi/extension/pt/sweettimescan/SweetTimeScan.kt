package eu.kanade.tachiyomi.extension.pt.sweettimescan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class SweetTimeScan : Madara(
    "Sweet Time Scan",
    "https://sweetscan.net",
    "pt-BR",
    SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    // The source has novels in text format, so we need to filter them.
    override fun searchMangaParse(response: Response): MangasPage {
        val mangaPage = super.searchMangaParse(response)
        val filteredResult = mangaPage.mangas.filter { it.title.contains(NOVEL_REGEX).not() }

        return MangasPage(filteredResult, mangaPage.hasNextPage)
    }

    // The site uses some image cache plugin that made the thumbnails don't load.
    // This removes the plugin site base URL and returns the direct image URL.
    override fun imageFromElement(element: Element): String {
        return baseUrl + super.imageFromElement(element)?.substringAfter(baseUrl)
    }

    companion object {
        private val NOVEL_REGEX = "novel|livro".toRegex(RegexOption.IGNORE_CASE)
    }
}
