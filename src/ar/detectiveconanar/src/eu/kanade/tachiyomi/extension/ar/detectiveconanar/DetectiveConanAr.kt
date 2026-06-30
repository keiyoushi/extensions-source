package eu.kanade.tachiyomi.extension.ar.detectiveconanar

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class DetectiveConanAr : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar"))
}
