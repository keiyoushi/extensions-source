package eu.kanade.tachiyomi.extension.ar.manganoon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class MangaNoon : MangaThemesia(
    "مانجا نون",
    "https://manjanoon.com",
    "ar",
    dateFormat = SimpleDateFormat("MMM d, yyy", Locale("ar")),
)
