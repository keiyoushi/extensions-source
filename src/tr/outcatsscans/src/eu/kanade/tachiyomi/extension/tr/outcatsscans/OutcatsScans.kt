package eu.kanade.tachiyomi.extension.tr.outcatsscans

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

// The site's admin-ajax archive query returns no series, so the lists are read from its archive pages
@Source
abstract class OutcatsScans : MadaraNoAjax() {

    // Chapters are for registered users only (a free account): a guest gets a notice instead of the pages
    override fun parsePages(document: Document): List<Page> {
        if (document.selectFirst(".content-blocked.login-required") != null) {
            throw Exception("Log in to the site in WebView to read this chapter (a free account is enough)")
        }
        return super.parsePages(document)
    }
}
