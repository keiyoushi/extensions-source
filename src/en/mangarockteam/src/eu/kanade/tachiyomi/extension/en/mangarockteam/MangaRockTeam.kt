package eu.kanade.tachiyomi.extension.en.mangarockteam
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaRockTeam : Madara("Manga Rock Team", "https://mangarockteam.com", "en")
