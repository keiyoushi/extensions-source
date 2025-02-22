package eu.kanade.tachiyomi.extension.ja.webcomicgammaplus
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.comicgamma.ComicGamma

class WebComicGammaPlus : ComicGamma("Web Comic Gamma Plus", "https://gammaplus.takeshobo.co.jp", "ja")
