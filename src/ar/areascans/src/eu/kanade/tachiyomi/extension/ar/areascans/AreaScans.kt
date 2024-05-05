package eu.kanade.tachiyomi.extension.ar.areascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class AreaScans : MangaThemesia(
    "Area Scans",
    "https://ar.areascans.org",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
)
