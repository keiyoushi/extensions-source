package eu.kanade.tachiyomi.extension.fr.rimuscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class RimuScans : MangaThemesia(
    "Rimu Scans",
    "https://rimuscans.fr",
    "fr",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH),
)
