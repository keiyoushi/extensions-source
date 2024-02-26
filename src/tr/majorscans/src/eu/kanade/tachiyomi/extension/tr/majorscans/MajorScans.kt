package eu.kanade.tachiyomi.extension.tr.majorscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class MajorScans : MangaThemesia(
    "MajorScans",
    "https://www.majorscans.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),
)
