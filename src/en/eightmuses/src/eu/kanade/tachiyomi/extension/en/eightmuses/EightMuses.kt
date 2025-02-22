package eu.kanade.tachiyomi.extension.en.eightmuses
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.eromuse.EroMuse
import kotlin.ExperimentalStdlibApi

@ExperimentalStdlibApi
class EightMuses : EroMuse("8Muses", "https://comics.8muses.com")
