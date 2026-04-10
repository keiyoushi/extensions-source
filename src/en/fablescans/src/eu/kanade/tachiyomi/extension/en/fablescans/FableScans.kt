package eu.kanade.tachiyomi.extension.en.fablescans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class FableScans :
    MangaThemesia(
        "Fable Scans",
        "https://fablescans.com",
        "en",
        mangaUrlDirectory = "/comic",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.ROOT),
    )
