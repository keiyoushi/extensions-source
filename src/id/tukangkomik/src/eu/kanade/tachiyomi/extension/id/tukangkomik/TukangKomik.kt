package eu.kanade.tachiyomi.extension.id.tukangkomik
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class TukangKomik : MangaThemesia("TukangKomik", "https://tukangkomik.co", "id")
