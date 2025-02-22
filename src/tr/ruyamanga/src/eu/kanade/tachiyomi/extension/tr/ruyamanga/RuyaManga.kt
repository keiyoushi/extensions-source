package eu.kanade.tachiyomi.extension.tr.ruyamanga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class RuyaManga : Madara(
    "Rüya Manga",
    "https://www.ruya-manga.com",
    "tr",
    SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH),
) {
    override val filterNonMangaItems = false
}
