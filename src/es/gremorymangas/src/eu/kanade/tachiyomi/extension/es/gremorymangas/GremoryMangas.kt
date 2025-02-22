package eu.kanade.tachiyomi.extension.es.gremorymangas

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class GremoryMangas : MangaThemesia(
    "Gremory Mangas",
    "https://gremorymangas.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
)
