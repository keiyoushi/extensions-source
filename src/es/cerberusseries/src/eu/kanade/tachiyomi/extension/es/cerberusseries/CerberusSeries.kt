package eu.kanade.tachiyomi.extension.es.cerberusseries
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class CerberusSeries : MangaThemesia(
    "Cerberus Series",
    "https://legionscans.com/wp",
    "es",
) {
    // Moved from custom to MangaThemesia
    override val versionId = 2
}
