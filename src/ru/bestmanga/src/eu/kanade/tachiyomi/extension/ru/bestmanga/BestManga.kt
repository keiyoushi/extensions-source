package eu.kanade.tachiyomi.extension.ru.bestmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class BestManga : Madara("BestManga", "https://bestmanga.club", "ru", SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()))
