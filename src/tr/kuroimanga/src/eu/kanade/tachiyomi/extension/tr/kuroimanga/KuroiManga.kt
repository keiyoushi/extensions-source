package eu.kanade.tachiyomi.extension.tr.kuroimanga

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class KuroiManga : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr"))
    override val chapterMode = ChapterMode.MangaAjax

    override fun parsePages(document: Document): List<Page> {
        val pageList = super.parsePages(document)

        if (
            pageList.isEmpty() &&
            document.select(".content-blocked, .login-required").isNotEmpty()
        ) {
            throw Exception("Bu seriyi okumak için abone olmalısınız")
        }
        return pageList
    }
}
