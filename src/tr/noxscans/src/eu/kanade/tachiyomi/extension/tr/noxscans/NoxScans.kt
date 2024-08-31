package eu.kanade.tachiyomi.extension.tr.noxscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class NoxScans : MangaThemesia(
    "NoxScans",
    "https://noxscans.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),
)
