package eu.kanade.tachiyomi.extension.pt.elevenscanlator
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga

class ElevenScanlator : ZeistManga("Eleven Scanlator", "https://elevenscanlator.blogspot.com", "pt-BR")
