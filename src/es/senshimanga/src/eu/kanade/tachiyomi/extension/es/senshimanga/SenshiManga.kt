package eu.kanade.tachiyomi.extension.es.senshimanga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.lectormoe.LectorMoe

class SenshiManga : LectorMoe(
    "Senshi Manga",
    "https://senshimanga.capibaratraductor.com",
    "es",
)
