package eu.kanade.tachiyomi.extension.en.hmanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class hManhwa : Madara(
    "hManhwa",
    "https://hmanhwa.com",
    "en",
    dateFormat = SimpleDateFormat("dd MMM", Locale.US),
)
