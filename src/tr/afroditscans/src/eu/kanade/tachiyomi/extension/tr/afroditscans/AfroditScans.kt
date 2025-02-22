package eu.kanade.tachiyomi.extension.tr.afroditscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class AfroditScans : MangaThemesia(
    "Afrodit Scans",
    "https://afroditscans.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM d, yyy", Locale("tr")),
)
