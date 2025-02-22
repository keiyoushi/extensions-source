package eu.kanade.tachiyomi.extension.id.komikzoid
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.colorlibanime.ColorlibAnime

class Komikzoid : ColorlibAnime("Komikzoid", "https://komikzoid.id", "id")
