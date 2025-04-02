package eu.kanade.tachiyomi.extension.ar.stormx

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Stormx : MangaThemesia(
    "Storm X",
    "https://www.stormx.site",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
)
