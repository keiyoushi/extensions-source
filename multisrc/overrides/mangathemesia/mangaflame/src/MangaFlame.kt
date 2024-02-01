package eu.kanade.tachiyomi.extension.ar.mangaflame

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class MangaFlame : MangaThemesia(
    "MangaFlame",
    "https://mangaflame.org",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
)
