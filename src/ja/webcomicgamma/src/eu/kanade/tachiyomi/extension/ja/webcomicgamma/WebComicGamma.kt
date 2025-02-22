package eu.kanade.tachiyomi.extension.ja.webcomicgamma
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.comicgamma.ComicGamma

class WebComicGamma : ComicGamma("Web Comic Gamma", "https://webcomicgamma.takeshobo.co.jp", "ja")
