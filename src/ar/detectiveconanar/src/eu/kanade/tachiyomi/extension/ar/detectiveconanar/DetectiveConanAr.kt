package eu.kanade.tachiyomi.extension.ar.detectiveconanar

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class DetectiveConanAr : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar"))
}
