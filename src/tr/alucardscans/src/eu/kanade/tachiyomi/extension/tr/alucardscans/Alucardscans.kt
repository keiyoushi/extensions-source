package eu.kanade.tachiyomi.extension.tr.alucardscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Alucardscans : MangaThemesia(
    "Alucard Scans",
    "https://alucardscans.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),
)
