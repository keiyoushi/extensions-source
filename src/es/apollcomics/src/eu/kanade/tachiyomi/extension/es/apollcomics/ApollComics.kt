package eu.kanade.tachiyomi.extension.es.apollcomics

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ApollComics : Madara("ApollComics", "https://apollcomics.xyz", "es", SimpleDateFormat("dd MMMM, yyyy", Locale("es")))
