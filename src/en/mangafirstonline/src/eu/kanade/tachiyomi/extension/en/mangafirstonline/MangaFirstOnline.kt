package eu.kanade.tachiyomi.extension.en.mangafirstonline
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaFirstOnline : Madara("Manga1st.online", "https://manga1st.online", "en")
