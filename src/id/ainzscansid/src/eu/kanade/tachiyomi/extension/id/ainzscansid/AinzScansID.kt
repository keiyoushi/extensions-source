package eu.kanade.tachiyomi.extension.id.ainzscansid
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class AinzScansID : MangaThemesia("Ainz Scans ID", "https://ainzscans.net", "id", "/series") {

    override val hasProjectPage = true
}
