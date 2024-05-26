package eu.kanade.tachiyomi.extension.ar.stellarsaber

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class StellarSaber : MangaThemesia(
    "السيف النجمي",
    "https://stellarsaber.pro",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
)
