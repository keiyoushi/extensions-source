package eu.kanade.tachiyomi.extension.ar.comicarab

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ComicArab : Madara(
    "كوميك العرب",
    "https://comicarab.com",
    "ar",
    dateFormat = SimpleDateFormat("dd MMMM، yyyy", Locale("ar")),
)
