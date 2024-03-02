package eu.kanade.tachiyomi.extension.ar.areamanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class AreaManga : MangaThemesia(
    "أريا مانجا",
    "https://www.areascans.net",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
)
