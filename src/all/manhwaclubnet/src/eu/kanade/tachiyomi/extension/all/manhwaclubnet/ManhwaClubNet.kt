package eu.kanade.tachiyomi.extension.all.manhwaclubnet
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response

class ManhwaClubNet(lang: String) : Madara(
    "ManhwaClub.net",
    "https://manhwaclub.net",
    lang,
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = super.chapterListParse(response)

        return when (lang) {
            "en" -> chapters.filterNot { it.name.endsWith(" raw") }
            "ko" -> chapters.filter { it.name.endsWith(" raw") }
            else -> emptyList()
        }
    }
}
