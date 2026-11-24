package eu.kanade.tachiyomi.extension.id.mangapoi

import eu.kanade.tachiyomi.multisrc.zmanga.ZManga
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Mangapoi : ZManga() {

    override val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)

    override val typeFilterValues = arrayOf(
        Pair("All", ""),
        Pair("Manga", "Manga"),
        Pair("Manhua", "Manhua"),
        Pair("Manhwa", "Manhwa"),
        Pair("One-shot", "One-shot"),
        Pair("Doujinshi", "Doujinshi"),
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val seriesUrl = if (url.pathSegments.first() == "series") {
            url.toString()
        } else {
            // Chapter pages link back to their series in the chapter navigation.
            client.get(url, Fetch.DOCUMENT).asJsoup()
                .selectFirst("#chapnav a[href*=/series/]")
                ?.absUrl("href")
                ?: return null
        }

        val document = client.get(seriesUrl, Fetch.DOCUMENT).asJsoup()
        return mangaDetailsParse(document).apply {
            setUrlWithoutDomain(seriesUrl)
            title = document.selectFirst(".series-title h2")!!.text()
        }
    }

    override fun pageListParse(document: Document): List<Page> = document.select("div.reader-area img[data-src]").mapIndexed { i, img ->
        Page(i, imageUrl = img.absUrl("data-src"))
    }
}
