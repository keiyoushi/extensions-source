package eu.kanade.tachiyomi.extension.it.shavelproiection

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ShavelProiection : Madara("ShavelProiection", "https://www.shavelproiection.com", "it", dateFormat = SimpleDateFormat("d MMM yyy", Locale("it")))
