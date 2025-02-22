package eu.kanade.tachiyomi.extension.es.cocorip
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class CocoRip : Madara("Coco Rip", "https://cocorip.net", "es", SimpleDateFormat("dd/MM/yyyy", Locale("es"))) {
    override val mangaDetailsSelectorDescription = "div.summary__content"
}
