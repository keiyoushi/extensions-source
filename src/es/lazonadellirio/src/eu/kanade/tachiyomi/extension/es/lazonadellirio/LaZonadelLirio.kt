package eu.kanade.tachiyomi.extension.es.lazonadellirio

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class LaZonadelLirio : MangaThemesia(
    "La Zona del Lirio",
    "https://lazonadellirio.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
)
