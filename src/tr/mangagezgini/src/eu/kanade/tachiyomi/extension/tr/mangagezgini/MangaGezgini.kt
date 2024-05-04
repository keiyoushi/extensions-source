package eu.kanade.tachiyomi.extension.tr.mangagezgini

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaGezgini : Madara(
    "MangaGezgini",
    "https://mangagezgini.com",
    "tr",
    SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),
)
