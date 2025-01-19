package eu.kanade.tachiyomi.extension.fr.mangasscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class MangasScans : MangaThemesia(
    "Mangas Scans",
    "https://mangas-scans.com",
    "fr",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH),
)
