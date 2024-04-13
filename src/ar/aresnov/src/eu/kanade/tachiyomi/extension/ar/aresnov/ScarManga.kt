package eu.kanade.tachiyomi.extension.ar.aresnov

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class ScarManga : MangaThemesia(
    "SCARManga",
    "https://scarmanga.com",
    "ar",
    mangaUrlDirectory = "/series",
    dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("ar")),
) {
    override val id = 1046935749022479891
}
