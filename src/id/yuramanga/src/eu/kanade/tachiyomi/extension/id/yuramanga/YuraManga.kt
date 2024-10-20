package eu.kanade.tachiyomi.extension.id.yuramanga

import eu.kanade.tachiyomi.multisrc.zmanga.ZManga
import java.text.SimpleDateFormat

class YuraManga : ZManga("YuraManga", "https://yuramanga.my.id", "id", SimpleDateFormat("dd/MM/yyyy")) {
    // Moved from Madara to ZManga
    override val versionId = 3
}
