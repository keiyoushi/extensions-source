package eu.kanade.tachiyomi.extension.tr.milasub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MilaSub : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr"))

    override val chapterMode = ChapterMode.MangaAjax

    override fun parseDetails(document: Document, id: String, preserveUrl: String?) = super.parseDetails(document.also { it.checkAccess() }, id, preserveUrl)

    override val chapterUrlSelector = "a:not(.chapter-thumbnail a)"

    override fun parsePages(document: Document) = super.parsePages(document).ifEmpty {
        document.checkAccess() ?: emptyList()
    }

    private fun Document.checkAccess() = selectFirst(".content-blocked, .login-required, title:contains(giriş yapın)")?.let {
        throw Exception(LOGIN_REQUIRED)
    }

    companion object {
        const val LOGIN_REQUIRED = "Bu bölümü görüntülemek için WebView'da giriş yapın"
    }
}
