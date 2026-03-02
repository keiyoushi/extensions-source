package eu.kanade.tachiyomi.extension.es.novamanhwa

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class NovaManhwa :
    MangaThemesia(
        "Nova Manhwas",
        "https://novamanhwa.cc",
        "es",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
    )
