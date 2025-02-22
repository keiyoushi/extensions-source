package eu.kanade.tachiyomi.extension.en.manga18club
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.manga18.Manga18

class Manga18Club : Manga18("Manga18.Club", "https://manga18.club", "en")
