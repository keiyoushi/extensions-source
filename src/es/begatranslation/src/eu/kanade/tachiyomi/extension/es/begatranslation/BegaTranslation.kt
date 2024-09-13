package eu.kanade.tachiyomi.extension.es.begatranslation

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class BegaTranslation : Madara(
    "Bega Translation",
    "https://begatranslation.com",
    "es",
    SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val useNewChapterEndpoint = true
    override val mangaSubString = "mangas"
}
