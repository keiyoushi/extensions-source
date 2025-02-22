package eu.kanade.tachiyomi.extension.en.mangaonlineteam
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaOnlineTeam : Madara("Manga Online Team", "https://mangaonlineteam.com", "en")
