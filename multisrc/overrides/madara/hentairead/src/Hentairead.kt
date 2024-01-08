package eu.kanade.tachiyomi.extension.en.hentairead

import android.net.Uri
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class Hentairead : Madara("HentaiRead", "https://hentairead.com", "en", dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)) {

    override val versionId: Int = 2

    override val mangaDetailsSelectorDescription = "div.post-sub-title.alt-title > h2"
    override val mangaDetailsSelectorAuthor = "div.post-meta.post-tax-wp-manga-artist > span.post-tags > a > span.tag-name"
    override val mangaDetailsSelectorArtist = "div.post-meta.post-tax-wp-manga-artist > span.post-tags > a > span.tag-name"
    override val mangaDetailsSelectorGenre = "div.post-meta.post-tax-wp-manga-genre > span.post-tags > a > span.tag-name"
    override val mangaDetailsSelectorTag = "div.post-meta.post-tax-wp-manga-tag > span.post-tags > a > span.tag-name"

    override val pageListParseSelector = "li.chapter-image-item > a > div.image-wrapper"

    override fun pageListParse(document: Document): List<Page> {
        super.countViews(document)

        return document.select(pageListParseSelector).mapIndexed { index, element ->
            var pageUri: String? = element.select("img").first()?.let {
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

    override fun chapterListParse(response: Response): List<SChapter> {
        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                setUrlWithoutDomain(response.request.url.encodedPath)
            },
        )
    }
}
