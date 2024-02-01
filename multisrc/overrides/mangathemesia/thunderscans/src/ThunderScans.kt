package eu.kanade.tachiyomi.extension.ar.thunderscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class ThunderScans : MangaThemesia(
    "Thunder Scans",
    "https://thunderscans.com",
    "ar",
    dateFormat = SimpleDateFormat("MMM d, yyy", Locale("ar")),
)
