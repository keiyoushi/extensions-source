package eu.kanade.tachiyomi.extension.ja.jmanga

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import java.text.SimpleDateFormat
import java.util.Locale

class JManga : WPComics("JManga", "https://jmanga.vip", "ja", SimpleDateFormat("MMMM", Locale.JAPANESE), null)
