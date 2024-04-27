package eu.kanade.tachiyomi.extension.ar.crowscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class CrowScans : MangaThemesia(
    "Crow Scans",
    "https://crowscans.com",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
)
