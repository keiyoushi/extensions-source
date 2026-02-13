package eu.kanade.tachiyomi.extension.pt.littletyrant

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.nodes.Element
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class LittleTyrant :
    Madara(
        "Little Tyrant",
        "https://tiraninha.world",
        "pt-BR",
        dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale("pt", "BR")),
    ) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .addNetworkInterceptor(::loginCheckInterceptor)
        .build()

    private fun loginCheckInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath == "/login/") {
            throw IOException("Faça login no WebView para ver os mangás")
        }
        return chain.proceed(request)
    }

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val mangaDetailsSelectorDescription = "div.manga-summary"

    // Layout modern-card-layout: itens em div.page-item-detail, link em div.post-title h3 a, thumb em div.content-top (background-image)
    override fun popularMangaSelector() = "div.page-item-detail"

    override val popularMangaUrlSelector = "div.post-title h3 a"

    private fun extractThumbnailUrlFromStyle(style: String): String? = REGEX_THUMBNAIL_URL.find(style)?.groupValues?.get(1)?.trim()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        with(element) {
            selectFirst(popularMangaUrlSelector)!!.let {
                manga.setUrlWithoutDomain(it.attr("abs:href"))
                manga.title = it.ownText()
            }
            // Thumbnail vem de div.content-top style="background-image:url('...')", não tem img
            selectFirst("div.content-top")?.attr("style")?.let { style ->
                extractThumbnailUrlFromStyle(style)?.let { url ->
                    manga.thumbnail_url = processThumbnail(url, true)
                }
            }
        }
        return manga
    }

    companion object {
        val REGEX_THUMBNAIL_URL = Regex("""url\s*\(\s*['"]?([^'")]+)['"]?\s*\)""")
    }
}
