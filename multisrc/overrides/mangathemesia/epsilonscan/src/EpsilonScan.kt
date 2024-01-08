package eu.kanade.tachiyomi.extension.fr.epsilonscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class EpsilonScan : MangaThemesia("Epsilon Scan", "https://epsilonscan.fr", "fr", dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH))
