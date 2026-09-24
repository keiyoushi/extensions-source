package eu.kanade.tachiyomi.extension.id.kaguya

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.minutes

@Source
abstract class Kaguya : Madara() {
    override fun OkHttpClient.Builder.configureClient() = readTimeout(1.minutes)

    // Fixed: Set to "series" to match https://02.kaguya.pro/series/...
    override val mangaSubString = "series"
    override val genreDirectory = "series-genre"

    // No postId
    override fun Element.postId() = "dummy"
    override fun mangaId(manga: SManga) = ""
    override fun parseArchive(document: Document) = super.parseArchive(document)
        .map {
            it.apply {
                url = memoPath(it)!!
            }
        }

    override val mangaDetailsSelectorTitle = "h1.post-title"
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div"
    override val mangaDetailsSelectorThumbnail = "head meta[property='og:image']"

    override fun imageFromElement(element: Element): String? {
        if (element.hasAttr("data-aesir")) {
            val decoded = Base64.decode(element.attr("data-aesir"), Base64.DEFAULT).toString(Charsets.UTF_8).trim()
            if (decoded.isNotEmpty()) return decoded
        }

        return super.imageFromElement(element)
            ?.takeIf { it.isNotEmpty() }
            ?: element.attr("content")
    }

    override val chapterMode = ChapterMode.MangaAjaxPaginated
}
