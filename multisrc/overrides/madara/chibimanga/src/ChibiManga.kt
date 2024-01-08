package eu.kanade.tachiyomi.extension.en.chibimanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ChibiManga : Madara("Chibi Manga", "https://www.cmreader.info", "en", dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US))
