package eu.kanade.tachiyomi.extension.en.wearehunger

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Wearehunger : Madara(
    "Wearehunger",
    "https://www.wearehunger.site",
    "en",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US),
)
