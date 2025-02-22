package eu.kanade.tachiyomi.extension.id.monzeekomik
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class MonzeeKomik : MangaThemesia(
    "Monzee Komik",
    "https://monzeekomik.my.id",
    "id",
) {
    override val hasProjectPage = true
}
