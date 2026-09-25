package eu.kanade.tachiyomi.extension.pt.acervohentai

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class AcervoHentai : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

    override val mangaSubString = "manhwa"
    override val genreDirectory = "tag-manhwa"

    override val chapterMode = ChapterMode.MangaAjax

    override val mangaDetailsSelectorStatus = "div.post-status .summary-heading:contains(Status) + .summary-content"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) {
        !it.encodedPath.startsWith("/wp-content/uploads/")
    }

    // Site lists chapters in ascending order
    override fun parseChapterList(document: Document, mangaPath: String) = super.parseChapterList(document, mangaPath).reversed()
}
