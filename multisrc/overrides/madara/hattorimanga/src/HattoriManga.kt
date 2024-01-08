package eu.kanade.tachiyomi.extension.tr.hattorimanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class HattoriManga : Madara(
    "Hattori Manga",
    "https://hattorimanga.com",
    "tr",
    SimpleDateFormat("d MMM yyy", Locale("tr")),
) {
    override fun pageListParse(document: Document): List<Page> {
        val blocked = document.select(".content-blocked").first()
        if (blocked != null) {
            throw Exception(blocked.text()) // Bu bölümü okumak için Üye olmanız gerekiyor.
        }

        return super.pageListParse(document)
    }
}
