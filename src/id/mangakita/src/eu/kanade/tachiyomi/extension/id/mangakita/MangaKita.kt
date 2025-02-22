package eu.kanade.tachiyomi.extension.id.mangakita
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class MangaKita : MangaThemesia("MangaKita", "https://mangakita.id", "id") {
    override val hasProjectPage = true
}
