package eu.kanade.tachiyomi.extension.id.klikmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class KlikManga : Madara("KlikManga", "https://klikmanga.id", "id", SimpleDateFormat("MMMM dd, yyyy", Locale("id")))
