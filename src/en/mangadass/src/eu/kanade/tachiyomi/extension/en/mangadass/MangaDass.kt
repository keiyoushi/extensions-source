package eu.kanade.tachiyomi.extension.en.mangadass

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaDass : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    override suspend fun getPopularManga(page: Int) = archivePage(page, "trending")

    override val archiveUrlSelector = "a"
    override val archiveTitleSelector = "h3"
    override fun nextPageSelector() = "ul.pagination li.next a"

    override val orderQueryParameter = "orderby"
    override val searchQueryParameter = "q"
    override fun archiveUrlBuilder(
        page: Int,
        order: String,
        path: String,
        query: String,
    ) = super.archiveUrlBuilder(page, order, path, query).apply {
        if (page > 1) removePathSegment(0)
    }
    override fun searchUrlBuilder(query: String) = super.searchUrlBuilder(query).addPathSegment("search")

    // No postId
    override fun Element.postId() = "dummy"
    override fun mangaId(manga: SManga) = ""
    override fun parseArchive(document: Document) = super.parseArchive(document)
        .map {
            it.apply {
                url = memoPath(it)!!
            }
        }

    override fun chapterListSelector() = ".row-content-chapter li"
    override val chapterDateSelector = ".chapter-time"

    override val pageListParseSelector = ".read-content img"

    override val filterGenresSelector = "div.container"
}
