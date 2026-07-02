package eu.kanade.tachiyomi.extension.es.bloomscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Bloomscans : MangaThemesia() {
    override val mangaUrlDirectory = "/series"
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("es"))

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty() && filters.isEmpty()) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("page")
                .addPathSegment(page.toString())
                .addQueryParameter("s", query)
                .addPathSegment("")
            return GET(url.build(), headers)
        }
        return super.searchMangaRequest(page, query, filters)
    }

    override val seriesTitleSelector = ".lrs-title"
    override val seriesThumbnailSelector = "img.lrs-cover"
    override val seriesDescriptionSelector = ".lrs-syn-wrap"
    override val seriesStatusSelector = ".lrs-infotable tr:contains(Status) td:last-child"
    override val seriesGenreSelector = ".lrs-genre"

    override fun chapterListSelector() = "#lrs-native-chapterlist li"

    private val imagesRegex = Regex(""""images":\[([^\]]+)]""")

    override fun pageListParse(document: Document): List<Page> {
        val script = document.selectFirst("script:containsData(ts_reader.run)")
            ?: return emptyList()

        val match = imagesRegex.find(script.data()) ?: return emptyList()

        val imagePaths = match.groupValues[1]
            .split(",")
            .map { it.trim().removeSurrounding("\"") }

        return imagePaths.mapIndexed { idx, url -> Page(idx, imageUrl = baseUrl + url) }
    }
}
