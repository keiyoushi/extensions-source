package eu.kanade.tachiyomi.extension.en.mangakitsune

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class MangaKitsune : Madara("MangaKitsune", "https://mangakitsune.com", "en", dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)) {

    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).reversed()
}
