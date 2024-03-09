package eu.kanade.tachiyomi.extension.en.hentairead

import android.net.Uri
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Hentairead : Madara("HentaiRead", "https://hentairead.com", "en", dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)) {

    override val versionId: Int = 2

    override val mangaSubString = "hentai"
    override val fetchGenres = false

    override fun getFilterList() = FilterList()

    override fun searchLoadMoreRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl${searchPage(page)}".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("post_type", "wp-manga")
            .build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = "div.c-tabs-item div.page-item-detail"

    override val mangaDetailsSelectorDescription = "div.post-sub-title.alt-title > h2"
    override val mangaDetailsSelectorAuthor = "div.post-meta.post-tax-wp-manga-artist > span.post-tags > a > span.tag-name"
    override val mangaDetailsSelectorArtist = "div.post-meta.post-tax-wp-manga-artist > span.post-tags > a > span.tag-name"
    override val mangaDetailsSelectorGenre = "div.post-meta.post-tax-wp-manga-genre > span.post-tags > a > span.tag-name"
    override val mangaDetailsSelectorTag = "div.post-meta.post-tax-wp-manga-tag > span.post-tags > a > span.tag-name"

    override val pageListParseSelector = "li.chapter-image-item > a > div.image-wrapper"

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            status = SManga.COMPLETED
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }

        return document.select(pageListParseSelector).mapIndexed { index, element ->
            val pageUri: String? = element.selectFirst("img")!!.let {
                it.absUrl(if (it.hasAttr("data-src")) "data-src" else "src")
            }
            Page(
                index,
                document.location(),
                Uri.parse(pageUri).buildUpon().clearQuery().appendQueryParameter("ssl", "1")
                    .appendQueryParameter("w", "1100").build().toString(),
            )
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(
            listOf(
                SChapter.create().apply {
                    name = "Chapter"
                    url = manga.url
                },
            ),
        )
    }
}
