package eu.kanade.tachiyomi.extension.id.shirakami

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Shirakami : MangaThemesia(
    "Shirakami",
    "https://shirakami.xyz",
    "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id")),
)
