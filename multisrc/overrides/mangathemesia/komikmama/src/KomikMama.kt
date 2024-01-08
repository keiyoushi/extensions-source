package eu.kanade.tachiyomi.extension.id.komikmama

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class KomikMama : MangaThemesia(
    "KomikMama",
    "https://komikmama.co",
    "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id")),
)
