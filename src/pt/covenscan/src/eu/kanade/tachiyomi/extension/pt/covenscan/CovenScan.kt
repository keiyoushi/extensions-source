package eu.kanade.tachiyomi.extension.pt.covenscan

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds

@Source
abstract class CovenScan : MadaraNoAjax() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 2.seconds)
    override val chapterMode = ChapterMode.MangaAjax

    // No postId
    override fun Element.postId() = "dummy"
    override fun mangaId(manga: SManga) = ""
    override fun parseArchive(document: Document) = super.parseArchive(document)
        .map {
            it.apply {
                url = memoPath(it)!!
            }
        }
    override val mangaDetailsSelectorAuthor = "div.post-content_item:contains(Author) > div.summary-content"
    override val supportsFilterFetching = false
}
