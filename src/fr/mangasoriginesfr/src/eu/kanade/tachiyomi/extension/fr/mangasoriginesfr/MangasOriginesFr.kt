package eu.kanade.tachiyomi.extension.fr.mangasoriginesfr

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangasOriginesFr : Madara("Mangas-Origines.fr", "https://mangas-origines.fr", "fr", SimpleDateFormat("dd/mm/yyyy", Locale("fr"))) {
    override val mangaSubString = "catalogues"

    // Manga Details Selectors
    override val mangaDetailsSelectorAuthor = "div.manga-authors > a"
    override val mangaDetailsSelectorDescription = "div.summary__content > p"
}
