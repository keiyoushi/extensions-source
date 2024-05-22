package eu.kanade.tachiyomi.extension.ar.hijala

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Hijala : MangaThemesia(
    "Hijala",
    "https://hijala.com",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
) {
    // Site moved from ZeistManga to MangaThemesia again
    override val versionId get() = 2
}
