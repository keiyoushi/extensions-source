package eu.kanade.tachiyomi.extension.id.komiklovers
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class KomikLovers : MangaThemesia(
    "Komik Lovers",
    "https://komiklovers.com",
    "id",
    "/komik",
) {
    override val hasProjectPage = true
}
