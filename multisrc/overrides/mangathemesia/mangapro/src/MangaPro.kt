package eu.kanade.tachiyomi.extension.ar.mangapro

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class MangaPro : MangaThemesia(
    "MangaPro",
    "https://mangapro.pro",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
)
