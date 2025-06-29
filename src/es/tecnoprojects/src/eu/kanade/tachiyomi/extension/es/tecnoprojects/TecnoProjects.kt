package eu.kanade.tachiyomi.extension.es.tecnoprojects

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class TecnoProjects : MangaThemesia(
    "TecnoProjects",
    "https://tecnoprojects.xyz",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
)
