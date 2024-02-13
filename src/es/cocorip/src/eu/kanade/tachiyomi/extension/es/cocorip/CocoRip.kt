package eu.kanade.tachiyomi.extension.es.cocorip

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class CocoRip : Madara("Coco Rip", "https://cocorip.net", "es", SimpleDateFormat("dd/MM/yyyy", Locale("es"))) {
    override val mangaDetailsSelectorDescription = "div.summary__content"
}
