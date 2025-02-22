package eu.kanade.tachiyomi.extension.it.walpurgisscan
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class WalpurgisScan : MangaThemesia("Walpurgi Scan", "https://www.walpurgiscan.it", "it", dateFormat = SimpleDateFormat("MMM d, yyyy", Locale("it"))) {
    override val id = 6566957355096372149
}
