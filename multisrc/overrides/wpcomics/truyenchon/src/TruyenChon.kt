package eu.kanade.tachiyomi.extension.vi.truyenchon

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers
import okhttp3.Request
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class TruyenChon : WPComics("TruyenChon", "http://truyenchon.com", "vi", SimpleDateFormat("dd/MM/yy", Locale.getDefault()), null) {
    override val searchPath = "the-loai"
    override fun headersBuilder(): Headers.Builder = Headers.Builder()
    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headersBuilder().add("Referer", baseUrl).build())
    override fun getFilterList(): FilterList {
        return FilterList(
            StatusFilter(getStatusList()),
            GenreFilter(getGenreList()),
        )
    }
    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("h3 a").let {
                title = it.text().replace("Truyện tranh ", "")
                setUrlWithoutDomain(it.attr("abs:href"))
            }
            thumbnail_url = imageOrNull(element.select("div.image:first-of-type img").first()!!)
        }
    }
    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.attr("title").replace("Truyện tranh ", "")
            setUrlWithoutDomain(element.attr("href"))
            thumbnail_url = imageOrNull(element.select("img").first()!!)
        }
    }
}
