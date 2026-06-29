package eu.kanade.tachiyomi.extension.ar.detectiveconanar

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class DetectiveConanAr :
    Madara(
        "شبكة كونان العربية",
        "https://manga.detectiveconanar.com",
        "ar",
        SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
    )
