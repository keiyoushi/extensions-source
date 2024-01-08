package eu.kanade.tachiyomi.extension.es.mangapt

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaPT : Madara(
    "MangaPT",
    "https://mangapt.com",
    "es",
    SimpleDateFormat("dd/MM/yyyy", Locale("es")),
)
