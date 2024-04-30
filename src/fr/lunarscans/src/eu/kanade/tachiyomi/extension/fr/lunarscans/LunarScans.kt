package eu.kanade.tachiyomi.extension.fr.lunarscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class LunarScans : MangaThemesia("Lunar Scans", "https://gloryscans.fr", "fr", dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH))
