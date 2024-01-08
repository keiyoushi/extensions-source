package eu.kanade.tachiyomi.extension.fr.astralmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class AstralManga : Madara("AstralManga", "https://astral-manga.fr", "fr", dateFormat = SimpleDateFormat("dd/mm/yyyy", Locale.FRANCE)) {
    override val useNewChapterEndpoint = true
}
