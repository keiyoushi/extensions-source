package eu.kanade.tachiyomi.extension.tr.mangawt

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class MangaWT : MangaThemesia("MangaWT", "https://mangawt.com", "tr", dateFormat = SimpleDateFormat("MMM d, yyyy", Locale("tr")))
