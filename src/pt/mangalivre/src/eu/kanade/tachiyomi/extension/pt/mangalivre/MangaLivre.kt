package eu.kanade.tachiyomi.extension.pt.mangalivre

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaLivre : Madara(
    "Manga Livre",
    "https://mangalivre.tv",
    "pt-BR",
    SimpleDateFormat("MMMM dd, yyyy", Locale("pt")),
) {
    override val useNewChapterEndpoint = true

    override val id: Long = 2834885536325274328
}
