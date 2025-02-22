package eu.kanade.tachiyomi.extension.id.sektekomik
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.colorlibanime.ColorlibAnime

class SekteKomik : ColorlibAnime("Sekte Komik", "https://sektekomik.xyz", "id")
