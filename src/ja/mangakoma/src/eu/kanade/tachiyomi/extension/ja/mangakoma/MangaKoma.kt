package eu.kanade.tachiyomi.extension.ja.mangakoma
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.liliana.Liliana

class MangaKoma : Liliana("Manga Koma", "https://mangakoma01.com", "ja")
