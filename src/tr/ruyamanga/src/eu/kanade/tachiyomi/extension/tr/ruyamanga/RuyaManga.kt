package eu.kanade.tachiyomi.extension.tr.ruyamanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class RuyaManga : Madara("Rüya Manga", "https://www.ruyamanga.com", "tr", SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("tr")))
