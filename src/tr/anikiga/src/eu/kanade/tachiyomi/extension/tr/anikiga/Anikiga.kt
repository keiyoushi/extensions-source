package eu.kanade.tachiyomi.extension.tr.anikiga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Anikiga : Madara("Anikiga", "https://anikiga.com", "tr", SimpleDateFormat("d MMMMM yyyy", Locale("tr")))
