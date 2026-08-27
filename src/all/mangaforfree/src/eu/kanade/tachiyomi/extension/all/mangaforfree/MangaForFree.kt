package eu.kanade.tachiyomi.extension.all.mangaforfree

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaForFree : Madara() {
    override val chapterMode = ChapterMode.AdminAjax

    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga {
        document.select("span.manga-title-badges").remove()
        return super.parseDetails(document, id, preserveUrl)
    }

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 1.seconds) { !it.encodedPath.startsWith("/wp-content/uploads/") }

    override fun chapterListSelector() = when (lang) {
        "en" -> "li.wp-manga-chapter:not(:contains(Raw))"
        "ko" -> "li.wp-manga-chapter:contains(Raw)"
        else -> super.chapterListSelector()
    }
}
