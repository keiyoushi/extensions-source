package eu.kanade.tachiyomi.extension.ar.manjanoon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Manjanoon : MangaThemesia(
    "مانجا نون",
    "https://manjanoon.com",
    "ar",
    dateFormat = SimpleDateFormat("MMM d, yyy", Locale("ar")),
)
