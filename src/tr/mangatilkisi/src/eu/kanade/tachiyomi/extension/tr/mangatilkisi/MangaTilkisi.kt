package eu.kanade.tachiyomi.extension.tr.mangatilkisi

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.post
import keiyoushi.utils.asJsoup
import okhttp3.FormBody
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaTilkisi : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("tr"))
    override val chapterMode = ChapterMode.MangaAjax

    override suspend fun fetchChapterDocument(chapterUrl: String): Document {
        val payload = FormBody.Builder()
            .add("verified", "1")
            .build()
        return client.post(chapterUrl, headers, payload).use { it.asJsoup() }
    }
}
