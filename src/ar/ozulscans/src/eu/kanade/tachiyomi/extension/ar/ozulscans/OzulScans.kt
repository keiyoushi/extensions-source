package eu.kanade.tachiyomi.extension.ar.ozulscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class OzulScans : MangaThemesia(
    "Ozul Scans",
    "https://kingofmanga.com",
    "ar",
    dateFormat = SimpleDateFormat("MMM d, yyy", Locale("ar")),
)
