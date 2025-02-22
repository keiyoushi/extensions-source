package eu.kanade.tachiyomi.extension.pt.guildatierdraw
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga

class GuildaTierDraw : ZeistManga("Guilda Tier Draw", "https://www.guildatierdraw.top", "pt-BR") {
    override val mangaDetailsSelectorDescription = "#Sinopse"
}
