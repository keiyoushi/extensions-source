package eu.kanade.tachiyomi.extension.fr.flamescansfr

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class LegacyScans : MangaThemesia("Legacy Scans", "https://legacy-scans.com", "fr", dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH)) {
    override val id = 8947802555328550956
}
