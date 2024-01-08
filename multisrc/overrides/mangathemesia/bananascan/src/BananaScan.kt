package eu.kanade.tachiyomi.extension.fr.bananascan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class BananaScan : MangaThemesia("Banana-Scan", "https://banana-scan.com", "fr", dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH))
