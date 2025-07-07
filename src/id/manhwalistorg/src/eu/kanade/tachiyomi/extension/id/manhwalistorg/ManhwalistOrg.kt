package eu.kanade.tachiyomi.extension.id.manhwalistorg

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class ManhwalistOrg : MangaThemesia(
    "Manhwalist.org",
    "https://isekaikomik.com",
    "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id")),
)
