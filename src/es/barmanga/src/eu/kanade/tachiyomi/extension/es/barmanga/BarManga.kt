package eu.kanade.tachiyomi.extension.es.barmanga

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class BarManga :
    Madara(
        "BarManga",
        "https://archiviumbar.com",
        "es",
        SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val mangaDetailsSelectorTitle = ".breadcrumb > li:last-child > a"

    override val pageListParseSelector = ".manga-reader-container > .manga-page-container"

    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }

        return document.select(pageListParseSelector).mapIndexedNotNull { index, element ->
            // The site tries to use a proxy first, but requires additional work to use. Fallback (no proxy) still works
            val encodedImageUrl = element.attr("data-url")
            val imageUrl = String(Base64.decode(encodedImageUrl, Base64.DEFAULT))

            Page(index, document.location(), imageUrl)
        }
    }
}
